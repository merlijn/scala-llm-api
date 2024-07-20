package com.github.merlijn.llm.examples.telegram_bot

import cats.effect.unsafe.IORuntime
import cats.effect.{Async, IO, IOApp}
import cats.syntax.all.*
import cats.{Monad, Parallel}
import com.github.merlijn.llm.api.{OpenAiClient, dto}
import org.http4s.blaze.client.BlazeClientBuilder
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.Uri
import telegramium.bots.high.*
import telegramium.bots.{ChatIntId, Message}

object ExampleTelegramBot extends IOApp.Simple:

  override val run =
    given ioRuntime: IORuntime = cats.effect.unsafe.IORuntime.global

    val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    val llmToken      = sys.env.get("LLM_TOKEN")
    val llmBaseUrl    = sys.env.getOrElse("LLM_BASE_URL", "https://api.openai.com/v1")
    val llmModel      = sys.env.getOrElse("LLM_MODEL", "gpt-3.5-turbo")
    val telegramToken = sys.env.getOrElse("TELEGRAM_BOT_TOKEN", throw new IllegalStateException("TELEGRAM_BOT_TOKEN env variable not set"))

    val botResource = for {
      openAiClientBackend <- HttpClientCatsBackend.resource[IO]()
      openAiClient = new OpenAiClient(
        apiToken = llmToken,
        backend = openAiClientBackend,
        baseUri = Uri.parse(llmBaseUrl).getOrElse(throw new IllegalStateException("Invalid base URL"))
      )
      telegramBackend <- BlazeClientBuilder[IO].resource
      api = BotApi(telegramBackend, baseUrl = s"https://api.telegram.org/bot$telegramToken")
    } yield new TelegramLLMBot(api, openAiClient, llmModel)

    botResource
      .use(_.start())
      .void

class TelegramLLMBot[F[_]: Async: Parallel: Monad](api: Api[F], llmClient: OpenAiClient[F], llmModel: String) extends LongPollBot[F](api):

  private val logger       = org.slf4j.LoggerFactory.getLogger(getClass)
  private val errorMessage = "An error occurred while processing your request. Please try again later."

  override def onMessage(msg: Message): F[Unit] =

    val chatRequest = dto.ChatCompletionRequest(
      model = llmModel,
      messages = List(
        dto.Message.system("Answer shortly"),
        dto.Message.user(msg.text.getOrElse(""))
      ),
      max_tokens = Some(1500),
      temperature = Some(0.8)
    )

    def reply(text: String) = api.execute(Methods.sendMessage(chatId = ChatIntId(msg.chat.id), text = text)).void

    llmClient
      .chatCompletion(chatRequest)
      .flatMap:
        case Right(response) =>
          response.firstMessageContent match
            case None          => reply(errorMessage)
            case Some(content) => reply(content)
        case Left(error) =>
          logger.error(s"LLM Request returned an error: ${error}")
          reply(errorMessage)
      .recover:
        case ex: Exception =>
          logger.error(s"Failed to send message to LLM or to send response to user: ${ex.getMessage}", ex)
          reply(errorMessage)
