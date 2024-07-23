package com.github.merlijn.llm.api

import pureconfig.*
import pureconfig.generic.derivation.default.*

case class LLMVendor(
  id: String,
  name: String,
  baseUrl: String,
  defaultModel: String,
  authenticationRequired: Boolean,
  apiToken: Option[String]
) derives ConfigReader
