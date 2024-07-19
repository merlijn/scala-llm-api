package com.github.merlijn.llm.api.schema

import io.circe.Derivation.summonLabelsRec
import io.circe.{Derivation, JsonObject}

import scala.compiletime.{constValue, erasedValue, summonFrom, summonInline}
import scala.deriving.Mirror
import scala.runtime.stdLibPatches.Predef.summon

case class DerivedSchema[T](
  name: String,
  fields: Array[String],
  children: Array[DerivedSchema[?]]
)

object DerivedSchema {

  given DerivedSchema[String] = new DerivedSchema[String]("string", Array.empty, Array.empty)
  given DerivedSchema[Int] = new DerivedSchema[Int]("string", Array.empty, Array.empty)

  inline final given derived[A](using A: Mirror.Of[A]): DerivedSchema[A] = {
    new DerivedSchema[A](
      constValue[A.MirroredLabel],
      Derivation.summonLabels[A.MirroredElemLabels],
      summonDescriptors[A.MirroredElemTypes]
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

case class Person(name: String, age: Int)


object Test extends App {

  val d = summon[DerivedSchema[Person]]

  println(d)
}
