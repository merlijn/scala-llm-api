package com.github.merlijn.llm.api

sealed trait ErrorResponse:
  def message: String

case class ToolNotFound(toolName: String) extends ErrorResponse:
  override def message: String = s"Tool with name $toolName not found"

case class UnexpectedError(message: String) extends ErrorResponse

case class JsonParsingError(reason: io.circe.Error) extends ErrorResponse:
  override def message: String = reason.getMessage
