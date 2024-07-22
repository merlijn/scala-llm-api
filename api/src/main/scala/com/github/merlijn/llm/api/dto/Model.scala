package com.github.merlijn.llm.api.dto

import com.github.merlijn.llm.api.camelToSnake
import com.github.merlijn.llm.api.schema.{ConcreteSchemaType, JsonSchemaTag, ReferenceType, SchemaType}

import scala.reflect.ClassTag

case class Message(role: String, content: Option[String], tool_call_id: Option[String] = None, tool_calls: Option[List[ToolCall]] = None)

case object Message:
  def user(content: String): Message                     = Message("user", Some(content))
  def assistant(content: String): Message                = Message("assistant", Some(content))
  def system(content: String): Message                   = Message("system", Some(content))
  def tool(toolCallId: String, content: String): Message = Message("tool", Some(content), tool_call_id = Some(toolCallId))

case class Model(
  id: String,
  `object`: String,
  created: Long,
  owned_by: String
)

case class ModelListResponse(
  data: List[Model]
)

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

object Tool:
  def function[T: ClassTag: JsonSchemaTag]: Tool =
    summon[JsonSchemaTag[T]].schemaType match
      case ReferenceType(ref) =>
        throw new IllegalArgumentException(s"Cannot create tool for reference type $ref")
      case concrete: ConcreteSchemaType =>
        val name = camelToSnake(summon[ClassTag[T]].runtimeClass.getSimpleName)  
        // todo require a description at compile time
        val description = concrete.description.getOrElse(name)
        Tool(function = Function(camelToSnake(summon[ClassTag[T]].runtimeClass.getSimpleName), description, concrete))

case class Function(
  name: String,
  description: String,
  parameters: SchemaType
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
):
  def firstMessageContent: Option[String] = choices.headOption.flatMap(_.message.content)

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