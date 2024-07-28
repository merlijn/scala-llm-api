package com.github.merlijn.llm.examples.function_call

import cats.effect.{IO, IOApp}
import cats.instances.future.*
import com.github.merlijn.llm.api.dto.*
import com.github.merlijn.llm.api.schema.{Description, JsonSchemaTag}
import com.github.merlijn.llm.api.{LLMVendor, OpenAiClient, ToolImplementation}
import io.circe.Decoder
import org.http4s.ember.client.EmberClientBuilder
import pureconfig.ConfigSource

@Description("Returns the status of a package by it's ID")
case class GetPackageById(
  @Description("The id of the package") package_id: String
) derives JsonSchemaTag, Decoder

object ExampleFunctionCall extends IOApp.Simple:

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def requireEnv(name: String): String =
    sys.env.getOrElse(name, throw new IllegalStateException(s"Environment variable $name not set"))

  val llmModel: String    = requireEnv("DEFAULT_CHAT_MODEL")
  val llmVendorId: String = requireEnv("DEFAULT_CHAT_VENDOR")

  val vendors = ConfigSource.default.at("vendors").load[List[LLMVendor]] match
    case Left(error)  => throw new IllegalStateException(s"Failed to load config: $error")
    case Right(value) => value

  val llmVendor = vendors.find(_.id == llmVendorId).getOrElse(throw new IllegalStateException(s"Vendor $llmVendorId not found"))

  val getPackageById = Tool.function[GetPackageById]

  val function: GetPackageById => IO[String] = getPackageById =>
    IO:
      logger.info(s"Function called for ${getPackageById.package_id}")
      Thread.sleep(500)
      s"The package with id ${getPackageById.package_id} is currently in transit, estimated delivery is tomorrow."

  val getPackageByIdImpl = ToolImplementation.fromFunction[IO, GetPackageById](function)

  override val run =
    EmberClientBuilder.default[IO].build.use { client =>
      val openAiClient = OpenAiClient.forVendor(llmVendor, client)

      val request = ChatCompletionRequest(
        model = llmModel,
        messages = List(
          Message.system("You are an assistant chat bot that helps customers with their questions about their packages"),
          Message.user("Hi there, do you know the status of my package? It's id is 471623-FGC")
        ),
        tools = Some(List(getPackageById))
      )

      openAiClient.chatCompletion(request, Seq(getPackageByIdImpl)).map:
        case Right(response) => response.firstMessageContent.foreach(logger.info)
        case Left(error)     => logger.error(error.message)
    }
