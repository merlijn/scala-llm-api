package com.github.merlijn.llm.api

import cats.Monad
import com.github.merlijn.llm.api
import com.github.merlijn.llm.api.dto.Tool
import com.github.merlijn.llm.api.schema.JsonSchemaTag
import io.circe.Decoder

import scala.reflect.ClassTag

class ToolImplementation[F[_]: Monad, T: Decoder](val name: String, val function: T => F[String], val spec: Tool):
  def apply(value: String): F[Either[ErrorResponse, String]] =
    io.circe.parser.decode[T](value) match
      case Left(error)  => Monad[F].pure(Left(JsonParsingError(error)))
      case Right(value) => Monad[F].map(function.apply(value))(Right(_))

object ToolImplementation:

  def fromFunction[F[_]: Monad, T: Decoder: ClassTag : JsonSchemaTag](function: T => F[String]): ToolImplementation[F, T] =

    val functionName = camelToSnake(summon[ClassTag[T]].runtimeClass.getSimpleName)
    val spec = Tool.function[T]
    new ToolImplementation[F, T](functionName, function, spec)
