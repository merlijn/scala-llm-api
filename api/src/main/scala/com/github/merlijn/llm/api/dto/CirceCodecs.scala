package com.github.merlijn.llm.api.dto

import io.circe._
import io.circe.generic.semiauto._

object CirceCodecs:

  given functionCodec: Codec[FunctionCall]                           = deriveCodec
  given toolCodec: Codec[ToolCall]                                   = deriveCodec
  given messageEncoder: Codec[Message]                               = deriveCodec
  given chatRequestEncoder: Codec[ChatCompletionRequest]             = deriveCodec
  given toolEncoder: Codec[Tool]                                     = deriveCodec
  given functionEncoder: Codec[Function]                             = deriveCodec
  given usageDecoder: Codec[Usage]                                   = deriveCodec
  given choiceDecoder: Decoder[Choice]                               = deriveDecoder
  given chatCompletionResponseCodec: Decoder[ChatCompletionResponse] = deriveDecoder
  given modelCodec: Codec[Model]                                     = deriveCodec
  given modelListResponseCodec: Decoder[ModelListResponse]           = deriveDecoder
  given chunkChoiceDecoder: Decoder[ChunkChoice]                     = deriveDecoder
  given chunkDecoder: Decoder[ChatCompletionChunk]                   = deriveDecoder
