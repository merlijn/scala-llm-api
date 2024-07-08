package com.github.merlijn.llm.api

import cats.Monad
import com.github.merlijn.llm.api.dto.{ErrorResponse, JsonParsingError}
import io.circe.Decoder

import scala.reflect.ClassTag

class ToolImplementation[F[_] : Monad, T](val name: String, decoder: Decoder[T], function: T => F[String]) {
  val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  def apply(value: String): F[Either[ErrorResponse, String]] = {
    logger.info(s"Decoding $value")

    io.circe.parser.decode[T](value)(decoder) match {
        case Left(error)  => Monad[F].pure(Left(JsonParsingError(error)))
        case Right(value) => Monad[F].map(function(value))(Right(_))
      }
  }
}

object ToolImplementation {

  def fromFunction[F[_] : Monad, T : Decoder : ClassTag](fn: T => F[String]): ToolImplementation[F, T] = {

    val functionName = camelToSnake(implicitly[ClassTag[T]].runtimeClass.getSimpleName)
    val decoder = implicitly[Decoder[T]]

    new ToolImplementation[F, T](functionName, decoder, fn)
  }
}
