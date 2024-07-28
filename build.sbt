import sbt.project

val commonSettings = Seq(
  organization := "com.github.merlijn",
  scalaVersion := "3.4.2",
  scalacOptions := Seq("-rewrite", "-indent")
)

val circeVersion = "0.14.9"
val logback = "ch.qos.logback" % "logback-classic" % "1.5.6"
val http4sVersion = "0.23.27"

lazy val api = project
  .in(file("api"))
  .settings(commonSettings)
  .settings(
    name := "llm-api",

    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "2.0.12",
      "io.circe"  %% "circe-core"     % circeVersion,
      "io.circe"  %% "circe-generic"   % circeVersion,
      "io.circe"  %% "circe-parser"    % circeVersion,
      "org.typelevel" %% "cats-core"   % "2.12.0",
      "org.http4s" %% "http4s-dsl"          % http4sVersion,
      "org.http4s" %% "http4s-client"       % http4sVersion,
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.7",
      "co.fs2" %% "fs2-core" % "3.10.2",
    )
  )

lazy val sharedResources = file("examples/shared-resources")

lazy val telegramBot = project
  .in(file("examples/telegram-bot"))
  .settings(commonSettings)
  .settings(
    name := "llm-api-example-telegram-bot",
    version := "0.1.0-SNAPSHOT",
    Compile / run / fork := true,
    Compile / resourceDirectory := sharedResources,
    libraryDependencies ++= Seq(
      "io.github.apimorphism" %% "telegramium-core" % "9.77.0",
      "io.github.apimorphism" %% "telegramium-high" % "9.77.0",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.7",
      logback,
    )
  ).dependsOn(api)

lazy val simpleChat = project
  .in(file("examples/simple-chat"))
  .settings(commonSettings)
  .settings(
    Compile / run / fork := true,
    Compile / resourceDirectory := sharedResources,
    name := "llm-api-example-simple-chat",
    version := "0.1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      logback,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
    )
  ).dependsOn(api)

lazy val functionCall = project
  .in(file("examples/function-call"))
  .settings(commonSettings)
  .settings(
    name := "example-function-call",
    version := "0.1.0-SNAPSHOT",
    Compile / resourceDirectory := sharedResources,
    libraryDependencies ++= Seq(
      logback,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
    )
  ).dependsOn(api)
