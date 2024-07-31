package com.github.merlijn.llm.examples.telegram_bot

import cats.effect.{IO, IOApp}
import com.github.merlijn.llm.api.{ChatConfig, LLMVendor, OpenAiClient}
import org.http4s.blaze.client.BlazeClientBuilder
import pureconfig.*
import pureconfig.generic.derivation.default.*
import telegramium.bots.high.*

case class ChatBotAppConfig(
  vendors: List[LLMVendor],
  defaultChatConfig: ChatConfig
) derives ConfigReader:
  def validVendors = vendors.filter(vendor => vendor.apiToken.isDefined || !vendor.authenticationRequired)

object ChatBotApp extends IOApp.Simple:

  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override val run =

    def requireEnv(name: String): String =
      sys.env.getOrElse(name, throw new IllegalStateException(s"Environment variable $name not set"))

    val config = ConfigSource.default.loadOrThrow[ChatBotAppConfig]

    logger.info(s"Number of valid vendors: ${config.validVendors.length}")

    val telegramToken = sys.env.getOrElse("TELEGRAM_BOT_TOKEN", throw new IllegalStateException("TELEGRAM_BOT_TOKEN env variable not set"))

    val botResource = for {
      httpClient <- BlazeClientBuilder[IO].resource
      llmClients  = config.validVendors.map(vendor => vendor.id -> OpenAiClient.forVendor[IO](vendor, httpClient)).toMap
      chatStorage = new ChatStorage[IO]()
      botApi      = BotApi(httpClient, baseUrl = s"https://api.telegram.org/bot$telegramToken")
    } yield new ChatBot(botApi, httpClient, config.validVendors, config.defaultChatConfig, chatStorage, Nil)

    botResource
      .use(_.start())
      .void
