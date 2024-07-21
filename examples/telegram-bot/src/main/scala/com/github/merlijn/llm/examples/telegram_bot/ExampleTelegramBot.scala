package com.github.merlijn.llm.examples.telegram_bot

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import com.github.merlijn.llm.api.OpenAiClient
import org.http4s.blaze.client.BlazeClientBuilder
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import sttp.model.Uri
import telegramium.bots.high.*

object ExampleTelegramBot extends IOApp.Simple:

  override val run =
    given ioRuntime: IORuntime = cats.effect.unsafe.IORuntime.global

    val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    val llmToken        = sys.env.get("LLM_TOKEN")
    val llmBaseUrl      = sys.env.getOrElse("LLM_BASE_URL", "https://api.openai.com/v1")
    val llmModel        = sys.env.getOrElse("LLM_MODEL", "gpt-3.5-turbo")
    val llmSystemPrompt = sys.env.getOrElse("LLM_SYSTEM_PROMPT", "You are a helpful assistant.")
    val telegramToken   = sys.env.getOrElse("TELEGRAM_BOT_TOKEN", throw new IllegalStateException("TELEGRAM_BOT_TOKEN env variable not set"))

    val botResource = for {
      openAiClientBackend <- HttpClientCatsBackend.resource[IO]()
      openAiClient = new OpenAiClient(
        apiToken = llmToken,
        backend = openAiClientBackend,
        baseUri = Uri.parse(llmBaseUrl).getOrElse(throw new IllegalStateException("Invalid base URL"))
      )
      chatStorage = new ChatStorage[IO]()
      telegramBackend <- BlazeClientBuilder[IO].resource
      api = BotApi(telegramBackend, baseUrl = s"https://api.telegram.org/bot$telegramToken")
    } yield new ChatBot(api, chatStorage, openAiClient, llmModel, llmSystemPrompt)

    botResource
      .use(_.start())
      .void
