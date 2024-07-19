package com.github.merlijn.llm.api.schema

import io.circe.JsonObject

type JsonType = "string" | "number" | "object" | "array" | "boolean" | "null"

sealed trait ConcreteSchemaType {
  def name: Option[String]
  def description: Option[String]
  def `type` : JsonType
}

case class RootSchema(
                       `$schema`: String,
                       `$id`: String,
                       defs: Map[String, ConcreteSchemaType] = Map.empty,
                     )

case class Reference(
                      ref: String
                    )

case class JsonEnumType(
                         options: List[String],
                       )

case class JsonObjectType(
                           name: Option[String],
                           description: Option[String],
                           parameters: Map[String, ConcreteSchemaType | Reference],
                           required: List[String] = List.empty
                         ) extends ConcreteSchemaType {
  def `type` : JsonType = "object"
}

case class JsonArrayType(
                          name: Option[String],
                          description: Option[String],
                          items: ConcreteSchemaType | Reference
                        ) extends ConcreteSchemaType {
  def `type` : JsonType = "array"
}

trait JsonSchema[T] {
  def asJson: JsonObject
}
