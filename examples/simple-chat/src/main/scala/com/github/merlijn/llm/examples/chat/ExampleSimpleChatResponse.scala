package com.github.merlijn.llm.examples.chat

import com.github.merlijn.llm.api._
import com.github.merlijn.llm.api.dto.{ChatCompletionRequest, Message}
import sttp.client3.HttpClientSyncBackend
import sttp.model.Uri

object ExampleSimpleChatResponse extends App:

  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  val llmToken: Option[String] = sys.env.get("LLM_TOKEN")
  val llmBaseUrl: String = sys.env.getOrElse("LLM_BASE_URL", "https://api.openai.com/v1")
  val llmModel: String = sys.env.getOrElse("LLM_MODEL", "gpt-3.5-turbo")

  val openAiClient = new OpenAiClient(
    apiToken = llmToken,
    backend = HttpClientSyncBackend(),
    baseUri = Uri.parse(llmBaseUrl).getOrElse(throw new IllegalStateException("Invalid base URL"))
  )

  val chatRequest = ChatCompletionRequest(
    model = llmModel,
    messages = List(
      Message.system("You are a helpful chat bot."),
      Message.user("Can you tell in short and simple language what an LLM is?"),
    )
  )

  openAiClient.chatCompletion(chatRequest) match
    case Right(response) => response.firstMessageContent.foreach(logger.info)
    case Left(error)     => logger.error(error.message)




