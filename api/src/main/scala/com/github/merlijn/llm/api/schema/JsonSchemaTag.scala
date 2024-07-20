package com.github.merlijn.llm.api.schema

import io.circe.{Encoder, JsonObject}

import scala.compiletime.*
import scala.deriving.Mirror
import scala.runtime.stdLibPatches.Predef.summon

// similar to ClassTag and TypeTag
case class JsonSchemaTag[T](schemaType: SchemaType) {
  def asJson: JsonObject = summon[Encoder[SchemaType]].apply(schemaType).asObject.get
}

object JsonSchemaTag {

  // predefined schemas
  given JsonSchemaTag[String] = JsonSchemaTag[String](ConcreteSchemaType("string"))
  given JsonSchemaTag[Float] = JsonSchemaTag[Float](ConcreteSchemaType("number"))
  given JsonSchemaTag[Double] = JsonSchemaTag[Double](ConcreteSchemaType("number"))
  given JsonSchemaTag[Int] = JsonSchemaTag[Int](ConcreteSchemaType("integer"))
  given JsonSchemaTag[Boolean] = JsonSchemaTag[Boolean](ConcreteSchemaType("boolean"))

  implicit def listSchema[T](using entries: JsonSchemaTag[T]): JsonSchemaTag[List[T]] = {
    JsonSchemaTag[List[T]](ConcreteSchemaType(`type` = "array", items = Some(entries.schemaType)))
  }

  // inline derivation of case classes
  inline final given derived[A](using A: Mirror.Of[A]): JsonSchemaTag[A] = {

    val childLabels  = summonLabelsRec[A.MirroredElemLabels].toVector
    val childSchemas = summonSchemasRec[A.MirroredElemTypes].toVector.map(_.schemaType)
    val childMeta = Description.fieldMetaForType[A].toMap
    val meta = Description.readMetaForType[A]

    val parameters: Map[String, SchemaType] =
      childLabels.zip(childSchemas).map {
        case (a, c: ConcreteSchemaType) => (a, c.copy(description = childMeta.get(a).map(_.description)))
        case (a, b) => (a, b)
      }.toMap

    JsonSchemaTag(ConcreteSchemaType(
      `type` = "object",
      description = meta.map(_.description),
      parameters = Some(parameters)
    ))
  }

  inline final def summonSchema[A]: JsonSchemaTag[A] = summonFrom {
    case decodeA: JsonSchemaTag[A] => decodeA
    case _: Mirror.Of[A] => JsonSchemaTag.derived[A]
  }

  inline final def summonSchemasRec[T <: Tuple]: List[JsonSchemaTag[?]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts) => summonSchema[t] :: summonSchemasRec[ts]
    }

  inline final def summonLabelsRec[T <: Tuple]: List[String] = inline erasedValue[T] match {
    case _: EmptyTuple => Nil
    case _: (t *: ts) => constValue[t].asInstanceOf[String] :: summonLabelsRec[ts]
  }
}

