package com.github.merlijn.llm.api

import cats.{Monad, Traverse}
import cats.data.EitherT
import cats.effect.Concurrent
import com.github.merlijn.llm.api.dto.CirceCodecs.given
import com.github.merlijn.llm.api.dto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.ci.CIStringSyntax

class OpenAiClient[F[_]: Monad: Concurrent](apiToken: Option[String], val backend: Client[F], val baseUri: Uri = uri"https://api.openai.com/v1"):

  private val logger                = org.slf4j.LoggerFactory.getLogger(getClass)
  private val jsonPrinter           = io.circe.Printer.noSpaces.copy(dropNullValues = true)
  private val authenticationHeaders = apiToken.map(token => Header.Raw(ci"Authorization", s"Bearer $token")).toList
  private val baseApiRequest        = Request[F](headers = Headers(authenticationHeaders))

  private def parseResponse(response: String): Either[ErrorResponse, ChatCompletionResponse] =
    logger.debug(s"Response body - $response")
    decode[ChatCompletionResponse](response).left.map(JsonParsingError(_))

  private def sendRequest(request: Request[F]): F[String] =
    logger.debug(s"Sending request - ${request.method} ${request.uri} - body: ${request.body}")
    backend.expect[String](request)

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

    val modelsUrl = baseUri / "models"
    val request = baseApiRequest
      .withMethod(Method.GET)
      .withUri(modelsUrl)

    Monad[F].map(backend.expect[String](request)) { response =>
      decode[ModelListResponse](response).map(_.data).left.map(JsonParsingError(_))
    }

  def chatCompletion(chatRequest: ChatCompletionRequest, toolImplementations: Seq[ToolImplementation[F, ?]] = Nil): F[Either[ErrorResponse, ChatCompletionResponse]] =

    val jsonBody: String = jsonPrinter.print(chatRequest.asJson)

    val request = baseApiRequest
      .withMethod(Method.POST)
      .withUri(baseUri / "chat" / "completions")
      .withEntity(jsonBody)
      .withHeaders(Headers(authenticationHeaders) ++ Headers(Header.Raw(ci"Content-Type", "application/json")))

    (for {
      responseBody          <- EitherT.right(sendRequest(request))
      chatResponse          <- EitherT.fromEither[F](parseResponse(responseBody))
      responseWithToolCalls <- performToolCalls(chatRequest, chatResponse, toolImplementations)
    } yield responseWithToolCalls).value

object OpenAiClient:
  def forVendor[F[_]: Monad: Concurrent](vendor: LLMVendor, backend: Client[F]): OpenAiClient[F] =
    new OpenAiClient[F](vendor.apiToken, backend, Uri.fromString(vendor.baseUrl).getOrElse(throw new IllegalStateException("Invalid base URL")))
