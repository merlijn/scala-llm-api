package com.github.merlijn.llm.api.schema

import io.circe.{Codec, Decoder, Encoder, HCursor, Json, JsonObject}
import io.circe.generic.semiauto.*

type JsonType = "string" | "number" | "integer" | "object" | "array" | "boolean" | "null"

sealed trait SchemaType

case class ConcreteSchemaType(
  `type`: JsonType,
  title: Option[String] = None,
  description: Option[String] = None,
  properties: Option[Map[String, SchemaType]] = None,
  required: Option[List[String]] = None,
  items: Option[SchemaType] = None,
  defs: Option[Map[String, SchemaType]] = None
) extends SchemaType

case class ReferenceType(
  ref: String
) extends SchemaType

object SchemaType:

  given jsonTypeEncoder: Encoder[JsonType] = Encoder.encodeString.contramap(identity)
  given jsonTypeDecoder: Decoder[JsonType] = Decoder.decodeString.emap:
    case "string"  => Right("string")
    case "number"  => Right("number")
    case "integer" => Right("integer")
    case "object"  => Right("object")
    case "array"   => Right("array")
    case "boolean" => Right("boolean")
    case "null"    => Right("null")
    case other     => Left(s"Unknown JSON type: $other")

  given referenceCodec: Codec.AsObject[ReferenceType] = deriveCodec[ReferenceType]

  extension (base: JsonObject)
    def addMaybe[T: Encoder](fieldName: String, value: Option[T]): JsonObject =
      value.fold(base)(v => base.add(fieldName, summon[Encoder[T]].apply(v)))

  given concreteSchemaTypeEncoder(using schemaEncoder: Encoder[SchemaType]): Encoder[ConcreteSchemaType] = Encoder.instance { a =>
    val base              = JsonObject("type" -> Encoder[JsonType].apply(a.`type`))
    val parametersEncoder = summon[Encoder[Map[String, SchemaType]]]

    def addMaybe[T: Encoder](base: JsonObject, fieldName: String, value: Option[T]): JsonObject =
      value.fold(base)(v => base.add(fieldName, summon[Encoder[T]].apply(v)))

    base
      .addMaybe("title", a.title)
      .addMaybe("description", a.description)
      .addMaybe("properties", a.properties)
      .addMaybe("required", a.required)
      .toJson
  }

  given schemaTypeDecoder: Decoder[SchemaType] = Decoder.decodeString.emap[SchemaType](str => Right(ReferenceType(str)))

  given concreteEncoder: Encoder[SchemaType] =
    Encoder.recursive { implicit recurse =>
      Encoder.instance:
        case c: ConcreteSchemaType => concreteSchemaTypeEncoder.apply(c)
        case r: ReferenceType      => referenceCodec.apply(r)
    }
