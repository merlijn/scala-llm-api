package com.github.merlijn.llm.api.dto

object CirceCodecs:
  import io.circe._
  import io.circe.generic.semiauto._

  given functionCodec: Codec[FunctionCall] = deriveCodec
  given toolCodec: Codec[ToolCall] = deriveCodec

  given messageEncoder: Codec[Message] = deriveCodec
  given chatRequestEncoder: Codec[ChatCompletionRequest] = deriveCodec
  given toolEncoder: Codec[Tool] = deriveCodec
  given functionEncoder: Codec[Function] = deriveCodec

  given usageDecoder: Codec[Usage] = deriveCodec

  given choiceDecoder: Decoder[Choice] = deriveDecoder

  given chatCompletionResponseCodec: Decoder[ChatCompletionResponse] = deriveDecoder
