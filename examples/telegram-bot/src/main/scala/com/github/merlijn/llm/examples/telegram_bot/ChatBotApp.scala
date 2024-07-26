package com.github.merlijn.llm.examples.telegram_bot

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, IOApp}
import com.github.merlijn.llm.api.{LLMVendor, OpenAiClient}
import org.http4s.blaze.client.BlazeClientBuilder
import pureconfig.*
import pureconfig.generic.derivation.default.*
import sttp.client3.httpclient.cats.HttpClientCatsBackend
import telegramium.bots.high.*

case class ChatConfig(
  vendorId: String,
  model: String,
  systemPrompt: String,
  maxHistory: Int = 100,
  temperature: Option[Double] = None,
  maxTokens: Option[Int] = Some(1000)
) derives ConfigReader

case class ChatBotAppConfig(
  vendors: List[LLMVendor],
  defaultChatConfig: ChatConfig
) derives ConfigReader:
  def validVendors = vendors.filter(vendor => vendor.apiToken.isDefined || !vendor.authenticationRequired)

object ChatBotApp extends IOApp.Simple:

  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override val run =
    given ioRuntime: IORuntime = cats.effect.unsafe.IORuntime.global

    val config = ConfigSource.default.load[ChatBotAppConfig] match
      case Left(error)  => throw new IllegalStateException(s"Failed to load config: $error")
      case Right(value) => value

    logger.info(s"Loaded config: $config")
    logger.info(s"Number of valid vendors: ${config.validVendors.length}")

    val telegramToken = sys.env.getOrElse("TELEGRAM_BOT_TOKEN", throw new IllegalStateException("TELEGRAM_BOT_TOKEN env variable not set"))

    val botResource = for {
      llmClientBackend <- HttpClientCatsBackend.resource[IO]()
      llmClients  = config.validVendors.map(vendor => vendor.id -> OpenAiClient.forVendor[IO](vendor, llmClientBackend)).toMap
      chatStorage = new ChatStorage[IO]()
      telegramBackend <- BlazeClientBuilder[IO].resource
      botApi = BotApi(telegramBackend, baseUrl = s"https://api.telegram.org/bot$telegramToken")
    } yield new ChatBot(botApi, llmClientBackend, config.vendors, config.defaultChatConfig, chatStorage, Nil)

    botResource
      .use(_.start())
      .void
