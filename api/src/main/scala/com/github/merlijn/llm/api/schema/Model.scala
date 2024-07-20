package com.github.merlijn.llm.api.schema

import io.circe.{Codec, Decoder, Encoder, HCursor, JsonObject}
import io.circe.generic.semiauto.*

type JsonType = "string" | "number" | "integer" | "object" | "array" | "boolean" | "null"

sealed trait SchemaType

case class ConcreteSchemaType(
  `type` : JsonType,
  title: Option[String] = None,
  description: Option[String] = None,
  parameters: Option[Map[String, SchemaType]] = None,
  required: Option[List[String]] = None,
  items: Option[SchemaType] = None,
  defs: Option[Map[String, SchemaType]] = None
) extends SchemaType

case class ReferenceType(
  ref: String
) extends SchemaType

trait JsonSchema[T] {
  def asJson: JsonObject
}

object SchemaType {

  implicit val jsonTypeEncoder: Encoder[JsonType] = Encoder.encodeString.contramap(identity)
  implicit val jsonTypeDecoder: Decoder[JsonType] = Decoder.decodeString.emap {
    case "string" => Right("string")
    case "number" => Right("number")
    case "integer" => Right("integer")
    case "object" => Right("object")
    case "array" => Right("array")
    case "boolean" => Right("boolean")
    case "null" => Right("null")
    case other => Left(s"Unknown JSON type: $other")
  }

  implicit val referenceCodec: Codec.AsObject[ReferenceType] = deriveCodec[ReferenceType]

  implicit def concreteSchemaTypeDecoder(implicit decoder: Decoder[SchemaType]): Decoder[ConcreteSchemaType] = new Decoder[ConcreteSchemaType] {
    final def apply(c: HCursor): Decoder.Result[ConcreteSchemaType] =
      for {
        `type` <- c.downField("type").as[JsonType]
        title  <- c.downField("title").as[Option[String]]
      } yield {
        ConcreteSchemaType(`type`, title)
      }
  }

  implicit def concreteSchemaTypeEncoder(implicit schemaEncoder: Encoder[SchemaType]): Encoder[ConcreteSchemaType] = Encoder.instance { a =>
      val base = JsonObject("type" -> Encoder[JsonType].apply(a.`type`))
      val parametersEncoder = summon[Encoder[Map[String, SchemaType]]]

      a.parameters.fold(base)(parameters => base.add("parameters", parametersEncoder.apply(parameters))).toJson

//      a.title.fold(base)(title => base.add("title", Encoder[String].apply(title))).toJson
    }

//  implicit val concreteEncoder: Encoder.AsObject[ConcreteSchemaType] =
//    implicit def treeDecoder[A: Encoder]: Encoder[SchemaType[A]] = {
//      Encoder.recursive { implicit recurse =>
//        List[Encoder[SchemaType[A]]](Encoder[ConcreteSchemaType[A]].widen, Encoder[ReferenceType[A]].widen).reduce(_ or _)
//      }
//    }

  implicit val concreteEncoder: Encoder[SchemaType] = {
      Encoder.recursive { implicit recurse =>
        Encoder.instance {
          case c: ConcreteSchemaType => concreteSchemaTypeEncoder.apply(c)
          case r: ReferenceType      => referenceCodec.apply(r)
        }
      }
    }
}
