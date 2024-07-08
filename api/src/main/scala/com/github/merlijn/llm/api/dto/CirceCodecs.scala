package com.github.merlijn.llm.api.dto

object CirceCodecs {
  import io.circe._
  import io.circe.generic.semiauto._

  implicit val functionCodec: Codec[FunctionCall] = deriveCodec
  implicit val toolCodec: Codec[ToolCall] = deriveCodec

  implicit val messageEncoder: Codec[Message] = deriveCodec
  implicit val chatRequestEncoder: Codec[ChatCompletionRequest] = deriveCodec
  implicit val toolEncoder: Codec[Tool] = deriveCodec
  implicit val functionEncoder: Codec[Function] = deriveCodec

  implicit val usageDecoder: Codec[Usage] = deriveCodec

  implicit val choiceDecoder: Decoder[Choice] = deriveDecoder

  implicit val chatCompletionResponseCodec: Decoder[ChatCompletionResponse] = deriveDecoder
}
