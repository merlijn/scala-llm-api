package com.github.merlijn.llm.api

import cats.data.EitherT
import cats.effect.Concurrent
import cats.{Monad, Traverse}
import com.github.merlijn.llm.api.dto.*
import com.github.merlijn.llm.api.dto.CirceCodecs.given
import fs2.Stream
import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.typelevel.ci.CIStringSyntax
import org.typelevel.jawn.Facade

import java.nio.charset.StandardCharsets

class OpenAiClient[F[_]: Monad: Concurrent](apiToken: Option[String], val backend: Client[F], val baseUri: Uri = uri"https://api.openai.com/v1"):

  private val logger                = org.slf4j.LoggerFactory.getLogger(getClass)
  private val jsonPrinter           = io.circe.Printer.noSpaces.copy(dropNullValues = true)
  private val authenticationHeaders = apiToken.map(token => Header.Raw(ci"Authorization", s"Bearer $token")).toList
  private val baseApiRequest        = Request[F](headers = Headers(authenticationHeaders))

  given f: Facade[Json] = new io.circe.jawn.CirceSupportParser(None, false).facade

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
      for
        toolImpl     <- EitherT.fromOption[F](toolImplementations.find(_.name == toolCall.function.name), ToolNotFound(toolCall.function.name))
        toolResponse <- EitherT(toolImpl(toolCall.function.arguments))
      yield Message.tool(toolCall.id, toolResponse)

    // note: only the first choice is considered
    response.choices.headOption.map(_.message) match
      case Some(msg @ Message(_, _, _, Some(toolCalls))) if toolCalls.nonEmpty =>
        for
          toolMessages <- Traverse[Seq].sequence(toolCalls.map(performToolCall(_)))
          nextRequest = chatRequest.copy(messages = chatRequest.messages ++ List(msg) ++ toolMessages)
          chatCompletion <- EitherT(chatCompletion(nextRequest, toolImplementations))
        yield chatCompletion
      case _ => EitherT.rightT(response)

  def listModels(): F[Either[ErrorResponse, List[Model]]] =

    val request = baseApiRequest
      .withMethod(Method.GET)
      .withUri(baseUri / "models")

    Monad[F].map(backend.expect[String](request)) { response =>
      decode[ModelListResponse](response).map(_.data).left.map(JsonParsingError(_))
    }

  private def httpCompletionRequest(chatRequest: ChatCompletionRequest): Request[F] =
    val jsonBody: String = jsonPrinter.print(chatRequest.asJson)
    baseApiRequest
      .withMethod(Method.POST)
      .withUri(baseUri / "chat" / "completions")
      .withEntity(jsonBody)
      .withHeaders(Headers(authenticationHeaders) ++ Headers(Header.Raw(ci"Content-Type", "application/json")))

  def chatCompletionStream(chatRequest: ChatCompletionRequest): Stream[F, ChatCompletionChunk] =

    val request = httpCompletionRequest(chatRequest.copy(stream = Some(true)))

    // TODO this is not optimal, but it works for now
    backend.stream(request)
      .flatMap(_.body.chunks)
      .map { chunk => new String(chunk.toArray, StandardCharsets.UTF_8) }
      .flatMap { chunkBody =>
        val result = chunkBody.split("\n").flatMap { line =>
          line.split("data:").last.trim match
            case "" | "[DONE]" => None
            case assumedJson =>
              io.circe.parser.parse(assumedJson).flatMap(_.deepDropNullValues.as[ChatCompletionChunk]) match
                case Right(chunk) => Some(chunk)
                case Left(e) =>
                  logger.error(s"Failed to parse chunk: $assumedJson", e)
                  throw e
        }

        Stream.emits(result)
      }

  def chatCompletion(chatRequest: ChatCompletionRequest, toolImplementations: Seq[ToolImplementation[F, ?]] = Nil): F[Either[ErrorResponse, ChatCompletionResponse]] =

    val request = httpCompletionRequest(chatRequest)

    (for {
      responseBody          <- EitherT.right(sendRequest(request))
      chatResponse          <- EitherT.fromEither[F](parseResponse(responseBody))
      responseWithToolCalls <- performToolCalls(chatRequest, chatResponse, toolImplementations)
    } yield responseWithToolCalls).value

object OpenAiClient:
  def forVendor[F[_]: Monad: Concurrent](vendor: LLMVendor, backend: Client[F]): OpenAiClient[F] =
    new OpenAiClient[F](vendor.apiToken, backend, Uri.fromString(vendor.baseUrl).getOrElse(throw new IllegalStateException("Invalid base URL")))
