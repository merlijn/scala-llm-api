package com.github.merlijn.llm.examples.chat

import com.github.merlijn.llm.api.*
import com.github.merlijn.llm.api.dto.{ChatCompletionRequest, Message}
import pureconfig.ConfigSource
import sttp.client3.HttpClientSyncBackend

object ExampleSimpleChatResponse extends App:

  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def requireEnv(name: String): String =
    sys.env.getOrElse(name, throw new IllegalStateException(s"Environment variable $name not set"))

  val llmModel: String    = requireEnv("DEFAULT_CHAT_MODEL")
  val llmVendorId: String = requireEnv("DEFAULT_CHAT_VENDOR")

  val vendors = ConfigSource.default.at("vendors").load[List[LLMVendor]] match
    case Left(error)  => throw new IllegalStateException(s"Failed to load config: $error")
    case Right(value) => value

  val llmVendor = vendors.find(_.id == llmVendorId).getOrElse(throw new IllegalStateException(s"Vendor $llmVendorId not found"))

  val openAiClient = OpenAiClient.forVendor(llmVendor, HttpClientSyncBackend())

  val chatRequest = ChatCompletionRequest(
    model = llmModel,
    messages = List(
      Message.system("You are a helpful chat bot."),
      Message.user("Can you tell in short and simple language what an LLM is?")
    )
  )

  openAiClient.chatCompletion(chatRequest) match
    case Right(response) => response.firstMessageContent.foreach(logger.info)
    case Left(error)     => logger.error(error.message)
