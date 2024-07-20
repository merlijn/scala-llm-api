import sbt.project

val scala_2_13_version = "2.13.4"
val scala_3_version = "3.4.2"

val commonSettings = Seq(
  organization := "com.github.merlijn",
)

val circeVersion = "0.14.9"

lazy val api = project
  .in(file("api"))
  .settings(commonSettings)
  .settings(
    name := "llm-api",
    scalaVersion := scala_3_version,
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "2.0.12",
      "com.softwaremill.sttp.client3" %% "core" % "3.9.7",
      "io.circe"   %% "circe-core"     % circeVersion,
       "io.circe"  %% "circe-generic"  % circeVersion,
       "io.circe"  %% "circe-parser"   % circeVersion,
//      "com.github.andyglow" %% "scala-jsonschema-core" % "0.7.11" cross CrossVersion.for3Use2_13,
//      "com.github.andyglow" %% "scala-jsonschema-circe-json" % "0.7.11" cross CrossVersion.for3Use2_13,
      "org.typelevel" %% "cats-core" % "2.12.0",
    )
  )

//lazy val telegramBot = project
//  .in(file("examples/telegram-bot"))
//  .settings(commonSettings)
//  .settings(
//    name := "llm-api-example-telegram-bot",
//    version := "0.1.0-SNAPSHOT",
//    scalaVersion := scala_2_13_version,
//    libraryDependencies ++= Seq(
//      "com.bot4s" %% "telegram-core" % "5.8.3" cross CrossVersion.for3Use2_13,
//      "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % "3.9.7",
//      "ch.qos.logback" % "logback-classic" % "1.5.6",
//    )
//  ).dependsOn(api)

lazy val simpleChat = project
  .in(file("examples/simple-chat"))
  .settings(commonSettings)
  .settings(
    name := "llm-api-example-simple-chat",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala_3_version,
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.5.6",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % "3.9.7",
    )
  ).dependsOn(api)

lazy val functionCall = project
  .in(file("examples/function-call"))
  .settings(commonSettings)
  .settings(
    name := "example-function-call",
    scalaVersion := scala_3_version,
    version := "0.1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.5.6",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % "3.9.7",
    )
  ).dependsOn(api)
