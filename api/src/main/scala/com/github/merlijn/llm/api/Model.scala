package com.github.merlijn.llm.api

import io.circe.JsonObject
import json.Schema
import json.schema.Version.Draft12

case class Message(role: String, content: String)

case object Message {
  def user(content: String): Message = Message("user", content)
  def system(content: String): Message = Message("system", content)
}

case class ChatCompletionRequest(
    model: String,
    messages: List[Message],
    temperature: Option[Double] = None,
    max_tokens: Option[Int] = None,
    n: Option[Int] = None,
    seed: Option[Int] = None,
    tools: Option[List[Tool]] = None
)

case class Tool(
  `type`: String = "function",
  function: Function
)

object Tool {
  def function[T](name: String, description: String)(implicit schema: Schema[T]): Tool = {
    import com.github.andyglow.jsonschema.AsCirce._
    import com.github.andyglow.jsonschema.AsValueBuilder._

    val json =
      schema.asCirce(Draft12("Test"))
        .asObject.get
        .remove("$schema")
        .remove("$id")

    Tool(function = Function(name, description, json))
  }
}

case class Function(
  name: String,
  description: String,
  parameters: JsonObject
)

case class ChatCompletionResponse(
   id: String,
   `object`: String,
   created: Long,
   model: String,
   choices: List[Choice],
   usage: Usage,
   system_fingerprint: Option[String]
)

case class Choice(
   index: Int,
   message: Message,
   finish_reason: String
)

case class Usage(
  prompt_tokens: Int,
  completion_tokens: Int,
  total_tokens: Int
)

sealed trait ErrorResponse {
  def message: String
}

case class UnexpectedError(message: String) extends ErrorResponse
case class JsonParsingError(reason: io.circe.Error) extends ErrorResponse {
  override def message: String = reason.getMessage
}
