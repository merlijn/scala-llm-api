package com.github.merlijn.llm.api.schema

import io.circe.syntax.*

case class Person(
                   @Description("The name of the person") name: String,
                   age: Int)


object Test extends App {

  val d = summon[JsonSchemaTag[Person]]

  val json = d.schemaType.asJson

  // pretty print the json
  println(json.spaces2)

//  println(d)
}