package com.github.merlijn.llm.api

import cats.Monad
import com.github.merlijn.llm.api.dto.{ErrorResponse, JsonParsingError}
import io.circe.Decoder

import scala.reflect.ClassTag

class ToolImplementation[F[_] : Monad, T : Decoder](val name: String, val function: T => F[String]):
  val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  def apply(value: String): F[Either[ErrorResponse, String]] =
    io.circe.parser.decode[T](value) match
      case Left(error)  => Monad[F].pure(Left(JsonParsingError(error)))
      case Right(value) => Monad[F].map(function.apply(value))(Right(_))

object ToolImplementation:

  def fromFunction[F[_] : Monad, T : Decoder : ClassTag](function: T => F[String]): ToolImplementation[F, T] =

    val functionName = camelToSnake(summon[ClassTag[T]].runtimeClass.getSimpleName)
    new ToolImplementation[F, T](functionName, function)
