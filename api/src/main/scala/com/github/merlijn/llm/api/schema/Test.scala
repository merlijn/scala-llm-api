package com.github.merlijn.llm.api.schema


case class Person(
   @Meta(title = "Name", description = "The name of the person") name: String,
   age: Int)


object Test extends App {

  val d = summon[DerivedSchema[Person]]

  println(d)
}