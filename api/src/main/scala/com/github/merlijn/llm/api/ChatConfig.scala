package com.github.merlijn.llm.api

import pureconfig.*
import pureconfig.generic.derivation.default.*

case class ChatConfig(
  vendorId: String,
  model: String,
  systemPrompt: String,
  maxHistory: Int = 100,
  temperature: Option[Double] = None,
  maxTokens: Option[Int] = Some(1000)
) derives ConfigReader
