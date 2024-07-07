package com.github.merlijn.llm.examples.function_call

import com.github.merlijn.llm.api.{ChatCompletionRequest, Message, OpenAiClient, Tool}
import json.{Json, Schema}
import sttp.client3.HttpClientSyncBackend
import sttp.model.Uri

case class GetPackageById(package_id: String)

object Main extends App {

  val llmToken = sys.env.get("LLM_TOKEN")
  val llmBaseUrl = sys.env.getOrElse("LLM_BASE_URL", "https://api.openai.com/v1")
  val llmModel = sys.env.getOrElse("LLM_MODEL", "gpt-4o")

  val openAiClient = new OpenAiClient(
    apiToken = llmToken,
    backend = HttpClientSyncBackend(),
    baseUri = Uri.parse(llmBaseUrl).getOrElse(throw new IllegalStateException("Invalid base URL"))
  )

  implicit val getPackageByIdSchema = Json.schema[GetPackageById]

  val request = ChatCompletionRequest(
    model = llmModel,
    messages = List(
      Message.system("You are an assistant chat bot that helps customers with their questions about their packages"),
      Message.user("Do you know what the status of my package is? The id is 237293GR"),
    ),
    tools = Some(List(Tool.function[GetPackageById]("get_package_by_id", "Get the status of a package by it's ID"))),
  )

  openAiClient.chatCompletion(request) match {
    case Right(response) => println(response.firstToolCall)
    case Left(error)     => println(error.message)
  }
}
