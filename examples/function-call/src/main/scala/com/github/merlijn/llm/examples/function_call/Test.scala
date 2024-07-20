//package com.github.merlijn.llm.examples.function_call
//
//import scala.concurrent.{Await, ExecutionContext, Future}
//import scala.concurrent.duration.DurationInt
//
//import scala.concurrent.ExecutionContext.Implicits.global
//
//object Test extends App {
//
//  val logger = org.slf4j.LoggerFactory.getLogger(getClass)
//
//  val asyncFunction: String => Future[String] = input =>
//    Future {
//      logger.info(s"Function called for ${input}")
//      "Output"
//    }
//
//  val syncFunction: String => String = input => "output"
//
//  println(syncFunction.apply("input"))
//
//  println(Await.result(asyncFunction.apply("input"), 3.seconds))
//}
