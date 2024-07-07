package com.github.merlijn.llm.examples.telegram_bot

import cats.instances.future._
import cats.syntax.functor._
import com.github.merlijn.llm.api
import com.github.merlijn.llm.api.{ChatCompletionRequest, OpenAiClient}
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{Polling, TelegramBot}
import com.bot4s.telegram.methods._
import com.bot4s.telegram.models._

import scala.concurrent.Future

class SimpleBot(telegramToken: String,
                llmClient: OpenAiClient[Future],
                llmModel: String) extends TelegramBot with Polling {

  override val client: RequestHandler[Future] = new FutureSttpClient(telegramToken)(llmClient.backend, scala.concurrent.ExecutionContext.global)

  override def receiveMessage(msg: Message): Future[Unit] =
    msg.text.fold(Future.successful(())) { text =>
      llmClient.chatCompletion(ChatCompletionRequest(
        messages = List(
          api.Message.system("answer shortly"),
          api.Message.user(text)
        ),
        model = llmModel,
        max_tokens = Some(1500),
        temperature = Some(0.8),
      )).flatMap { completion => completion match {
          case Right(response) =>
            logger.debug(s"Received response from LLM: ${response.choices.head.message.content}")

            response.firstMessageContent match {
              case None          => request(SendMessage(msg.source, "Failed to get response from LLM.")).void
              case Some(content) => request(SendMessage(msg.source, content, parseMode = Some(ParseMode.Markdown))).void
            }

          case Left(error) =>
            logger.error(s"Failed to get response from LLM: ${error.message}")
            request(SendMessage(msg.source, "An error occurred while processing your request. Please try again later.")).void
        }
      }.recover {
        case ex: Exception =>
          logger.error(s"Failed to send message to LLM or to send response to user: ${ex.getMessage}")
          request(SendMessage(msg.source, "An error occurred while processing your request. Please try again later.")).void
      }
    }
}
