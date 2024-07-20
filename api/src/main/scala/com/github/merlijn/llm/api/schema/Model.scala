package com.github.merlijn.llm.api.schema

import io.circe.JsonObject

type JsonType = "string" | "number" | "integer" | "object" | "array" | "boolean" | "null"

sealed trait SchemaType

case class ConcreteSchemaType(
  `type` : JsonType,
  title: Option[String] = None,
  description: Option[String] = None,
  parameters: Map[String, SchemaType] = Map.empty,
  required: List[String] = List.empty,
  items: Option[SchemaType] = None
) extends SchemaType

case class ReferenceType(
  ref: String
) extends SchemaType

case class RootSchema(
 `$schema`: String,
 `$id`: String,
 schemaType: ConcreteSchemaType,                    
 defs: Map[String, ConcreteSchemaType] = Map.empty,
)

trait JsonSchema[T] {
  def asJson: JsonObject
}
