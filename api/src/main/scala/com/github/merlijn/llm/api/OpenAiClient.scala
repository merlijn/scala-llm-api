package com.github.merlijn.llm.api

import cats.{Monad, Traverse}
import cats.data.EitherT
import com.github.merlijn.llm.api.dto.CirceCodecs.given
import com.github.merlijn.llm.api.dto._
import io.circe.parser.decode
import io.circe.syntax._
import sttp.client3.{Identity, RequestT, SttpBackend, UriContext, basicRequest}
import sttp.model.Uri

class OpenAiClient[F[_]: Monad](apiToken: Option[String], val backend: SttpBackend[F, ?], val baseUri: Uri = uri"https://api.openai.com/v1"):

  private val logger                = org.slf4j.LoggerFactory.getLogger(getClass)
  private val jsonPrinter           = io.circe.Printer.noSpaces.copy(dropNullValues = true)
  private val authenticationHeaders = apiToken.map(token => "Authorization" -> s"Bearer $token").toMap
  private val baseApiRequest        = basicRequest.headers(authenticationHeaders)

  private def parseResponse(response: String): Either[ErrorResponse, ChatCompletionResponse] =
    logger.debug(s"Response body - $response")
    decode[ChatCompletionResponse](response).left.map(JsonParsingError(_))

  private def sendRequest(request: RequestT[Identity, Either[String, String], Any]): F[Either[ErrorResponse, String]] =
    logger.debug(s"Sending request - ${request.method} ${request.uri} - body: ${request.body}")
    Monad[F].map(request.send(backend)) { _.body.left.map(e => UnexpectedError(e)) }

  private def performToolCalls(
    chatRequest: ChatCompletionRequest,
    response: ChatCompletionResponse,
    toolImplementations: Seq[ToolImplementation[F, ?]]
  ): EitherT[F, ErrorResponse, ChatCompletionResponse] =

    def performToolCall(toolCall: ToolCall): EitherT[F, ErrorResponse, Message] =
      for {
        toolImpl     <- EitherT.fromOption[F](toolImplementations.find(_.name == toolCall.function.name), ToolNotFound(toolCall.function.name))
        toolResponse <- EitherT(toolImpl(toolCall.function.arguments))
      } yield Message.tool(toolCall.id, toolResponse)

    // note: only the first choice is considered
    response.choices.headOption.map(_.message) match
      case Some(msg @ Message(_, _, _, Some(toolCalls))) if toolCalls.nonEmpty =>
        for {
          toolMessages <- Traverse[Seq].sequence(toolCalls.map(performToolCall(_)))
          nextRequest = chatRequest.copy(messages = chatRequest.messages ++ List(msg) ++ toolMessages)
          chatCompletion <- EitherT(chatCompletion(nextRequest, toolImplementations))
        } yield chatCompletion
      case _ => EitherT.fromEither[F](Right(response))

  def listModels(): F[Either[ErrorResponse, List[Model]]] =

    val modelsUrl = baseUri.addPath("models")
    val request   = baseApiRequest.get(modelsUrl)

    (for {
      responseBody <- EitherT(sendRequest(request))
      response     <- EitherT.fromEither[F](decode[ModelListResponse](responseBody)).leftMap(JsonParsingError(_))
    } yield response.data).value

  def chatCompletion(chatRequest: ChatCompletionRequest, toolImplementations: Seq[ToolImplementation[F, ?]] = Nil): F[Either[ErrorResponse, ChatCompletionResponse]] =

    val completionUrl    = baseUri.addPath("chat", "completions")
    val jsonBody: String = jsonPrinter.print(chatRequest.asJson)

    val request = baseApiRequest
      .header("Content-Type", "application/json")
      .post(completionUrl)
      .body(jsonBody)

    (for {
      responseBody          <- EitherT(sendRequest(request))
      chatResponse          <- EitherT.fromEither[F](parseResponse(responseBody))
      responseWithToolCalls <- performToolCalls(chatRequest, chatResponse, toolImplementations)
    } yield responseWithToolCalls).value
