package com.github.merlijn.llm.api.schema

import scala.compiletime.*
import scala.deriving.Mirror
import scala.runtime.stdLibPatches.Predef.summon

case class DerivedSchema[T](schemaType: SchemaType)

object DerivedSchema {

  // predefined schemas
  given DerivedSchema[String] = DerivedSchema[String](ConcreteSchemaType("string"))
  given DerivedSchema[Float] = DerivedSchema[Float](ConcreteSchemaType("number"))
  given DerivedSchema[Double] = DerivedSchema[Double](ConcreteSchemaType("number"))
  given DerivedSchema[Int] = DerivedSchema[Int](ConcreteSchemaType("integer"))
  given DerivedSchema[Boolean] = DerivedSchema[Boolean](ConcreteSchemaType("boolean"))

  implicit def listSchema[T](using entries: DerivedSchema[T]): DerivedSchema[List[T]] = {
    DerivedSchema[List[T]](ConcreteSchemaType(`type` = "array", items = Some(entries.schemaType)))
  }

  // inline derivation of case classes
  inline final given derived[A](using A: Mirror.Of[A]): DerivedSchema[A] = {

    val childLabels  = summonLabelsRec[A.MirroredElemLabels].toVector
    val childSchemas = summonDescriptorsRec[A.MirroredElemTypes].toVector.map(_.schemaType)
    val childMeta = Meta.fieldMetaForType[A].toMap
    val meta = Meta.readMetaForType[A]

    val parameters: Map[String, SchemaType] =
      childLabels.zip(childSchemas).map {
        case (a, c: ConcreteSchemaType) => (a, c.copy(title = childMeta.get(a).map(_.title), description = childMeta.get(a).map(_.description)))
        case (a, b) => (a, b)
      }.toMap

    DerivedSchema(ConcreteSchemaType(
      `type` = "object",
      title = meta.map(_.title),
      description = meta.map(_.description),
      parameters = parameters
    ))
  }

  inline final def summonDescriptor[A]: DerivedSchema[A] = summonFrom {
    case decodeA: DerivedSchema[A] => decodeA
    case _: Mirror.Of[A] => DerivedSchema.derived[A]
  }

  inline final def summonDescriptorsRec[T <: Tuple]: List[DerivedSchema[?]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts) => summonDescriptor[t] :: summonDescriptorsRec[ts]
    }

  inline final def summonLabelsRec[T <: Tuple]: List[String] = inline erasedValue[T] match {
    case _: EmptyTuple => Nil
    case _: (t *: ts) => constValue[t].asInstanceOf[String] :: summonLabelsRec[ts]
  }
}

