package com.github.merlijn.llm.api

import cats.Monad
import sttp.client3.{SttpBackend, UriContext, basicRequest}
import sttp.model.Uri
import CirceCodecs._
import io.circe.syntax._
import io.circe.parser.decode

class OpenAiClient[F[_] : Monad, P](
     apiToken: String,
     val backend: SttpBackend[F, P],
     val baseUri: Uri = uri"https://api.openai.com/v1") {

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  private val jsonPrinter = io.circe.Printer.noSpaces.copy(dropNullValues = true)

  def chatCompletion(chatRequest: ChatCompletionRequest): F[Either[ErrorResponse, ChatCompletionResponse]] = {

    val completionUrl = baseUri.addPath("chat", "completions")
    val jsonBody = jsonPrinter.print(chatRequest.asJson)

    logger.debug(s"POST $completionUrl - $jsonBody")

    val request = basicRequest.post(completionUrl)
      .header("Authorization", s"Bearer $apiToken")
      .header("Content-Type", "application/json")
      .body(jsonBody)

    def parseResponse(response: String): Either[ErrorResponse, ChatCompletionResponse] = {

      logger.debug(s"Response body - $response")

      decode[ChatCompletionResponse](response) match {
        case Right(value) => Right(value)
        case Left(error)  => Left(JsonParsingError(error))
      }
    }

    implicitly[Monad[F]]
      .map(request.send(backend)) { response =>
        response.body.left.map(e => UnexpectedError(e)).flatMap(parseResponse)
      }
  }
}
