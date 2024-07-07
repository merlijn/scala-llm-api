package com.github.merlijn.llm.examples.telegram_bot

import cats.instances.future._
import com.github.merlijn.llm.api.OpenAiClient
import sttp.client3.HttpClientFutureBackend
import sttp.model.Uri

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object Main extends App {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  val llmToken = sys.env.get("LLM_TOKEN")
  val llmBaseUrl = sys.env.getOrElse("LLM_BASE_URL", "https://api.openai.com/v1")
  val llmModel = sys.env.getOrElse("LLM_MODEL", "gpt-3.5-turbo")
  val telegramToken = sys.env.getOrElse("TELEGRAM_TOKEN", throw new IllegalStateException("TELEGRAM_TOKEN env variable not set"))

  val openAiClient = new OpenAiClient(
    apiToken = llmToken,
    backend = HttpClientFutureBackend(),
    baseUri = Uri.parse(llmBaseUrl).getOrElse(throw new IllegalStateException("Invalid base URL"))
  )

  val bot = new SimpleBot(
    telegramToken = telegramToken,
    llmClient = openAiClient,
    llmModel = llmModel
  )

  val eol = bot.run()
  scala.io.StdIn.readLine()
  bot.shutdown() // initiate shutdown

  // Wait for the bot end-of-life
  Await.result(eol, Duration.Inf)
}
