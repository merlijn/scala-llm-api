package com.github.merlijn.llm.api.dto

import com.github.merlijn.llm.api.camelToSnake
import io.circe.JsonObject
import json.Schema
import json.schema.Version.Draft12

import scala.reflect.ClassTag

case class Message(role: String, content: Option[String], tool_call_id: Option[String] = None, tool_calls: Option[List[ToolCall]] = None)

case object Message {
  def user(content: String): Message = Message("user", Some(content))
  def system(content: String): Message = Message("system", Some(content))
  def tool(toolCallId: String, content: String): Message = Message("tool", Some(content), tool_call_id = Some(toolCallId))
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
  def function[T : ClassTag](description: String)(implicit schema: Schema[T]): Tool = {
    import com.github.andyglow.jsonschema.AsCirce._
    import com.github.andyglow.jsonschema.AsValueBuilder._

    val name = camelToSnake(implicitly[ClassTag[T]].runtimeClass.getSimpleName)

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

// --- Response

case class ChatCompletionResponse(
   id: String,
   `object`: String,
   created: Long,
   model: String,
   choices: List[Choice],
   usage: Usage,
   system_fingerprint: Option[String]
) {
  def firstMessageContent: Option[String] = choices.headOption.flatMap(_.message.content)
}

case class Choice(
   index: Int,
   message: Message,
   finish_reason: Option[String] // None in case of streaming
)

case class Usage(
  prompt_tokens: Int,
  completion_tokens: Int,
  total_tokens: Int
)

case class ToolCall(
  id: String,
  `type`: String,
  function: FunctionCall
)

case class FunctionCall(
  name: String,
  arguments: String
)

sealed trait ErrorResponse {
  def message: String
}

case class ToolNotFound(toolName: String) extends ErrorResponse {
  override def message: String = s"Tool with name $toolName not found"
}
case class UnexpectedError(message: String) extends ErrorResponse
case class JsonParsingError(reason: io.circe.Error) extends ErrorResponse {
  override def message: String = reason.getMessage
}
