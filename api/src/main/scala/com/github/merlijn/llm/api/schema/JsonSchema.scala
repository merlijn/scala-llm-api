package com.github.merlijn.llm.api.schema

import io.circe.Derivation
import io.circe.Derivation.summonLabelsRec

import scala.compiletime.*
import scala.deriving.Mirror
import scala.runtime.stdLibPatches.Predef.summon

case class DerivedSchema[T](
  name: String,
  fields: Array[String],
  children: Array[DerivedSchema[?]],
  descriptions: Vector[(String, Meta)]
) {
  override def toString = {
    s"""
      |DerivedSchema(
      |   name = $name,
      |   fields = ${fields.mkString("[", ", ", "]")},
      |   children = ${children.mkString("[", ", ", "]")},
      |   meta = ${descriptions.map((k, v) => s"($k -> ${v.name}, ${v.description})").mkString("[", ", ", "]")}
      |
      |""".stripMargin
  }
}

object DerivedSchema {

  // predefined schemas
  given DerivedSchema[String] = new DerivedSchema[String]("string", Array.empty, Array.empty, Vector.empty)
  given DerivedSchema[Int] = new DerivedSchema[Int]("string", Array.empty, Array.empty, Vector.empty)
  given DerivedSchema[Boolean] = new DerivedSchema[Boolean]("boolean", Array.empty, Array.empty, Vector.empty)

  inline final given derived[A](using A: Mirror.Of[A]): DerivedSchema[A] = {
    new DerivedSchema[A](
      constValue[A.MirroredLabel],
      Derivation.summonLabels[A.MirroredElemLabels],
      summonDescriptors[A.MirroredElemTypes],
      Meta.fieldMetaForType[A]
    )
  }

  inline final def summonDescriptor[A]: DerivedSchema[A] = summonFrom {
    case decodeA: DerivedSchema[A] => decodeA
    case _: Mirror.Of[A] => DerivedSchema.derived[A]
  }

  inline final def summonDescriptors[T <: Tuple]: Array[DerivedSchema[?]] = summonDescriptorsRec[T].toArray

  inline final def summonDescriptorsRec[T <: Tuple]: List[DerivedSchema[?]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts) => summonDescriptor[t] :: summonDescriptorsRec[ts]
    }
}

case class Person(
   @Meta(name = "Name", description = "The name of the person") name: String,
   age: Int)


object Test extends App {

  val d = summon[DerivedSchema[Person]]

  println(d)
}
