package com.github.merlijn.llm.api

object CirceCodecs {
  import io.circe._
  import io.circe.generic.semiauto._

  implicit val messageEncoder: Codec[Message] = deriveCodec
  implicit val chatRequestEncoder: Codec[ChatCompletionRequest] = deriveCodec
  implicit val toolEncoder: Codec[Tool] = deriveCodec
  implicit val functionEncoder: Codec[Function] = deriveCodec

  implicit val chatCompletionResponseDecoder: Codec[ChatCompletionResponse] = deriveCodec
  implicit val choiceDecoder: Codec[Choice] = deriveCodec
  implicit val usageDecoder: Codec[Usage] = deriveCodec
}
