package com.github.merlijn.llm.examples.function_call

import com.github.merlijn.llm.api.dto.*
import com.github.merlijn.llm.api.schema.{Description, JsonSchemaTag}
import com.github.merlijn.llm.api.{LLMVendor, OpenAiClient, ToolImplementation}
import io.circe.{Decoder, Printer}
import sttp.client3.HttpClientFutureBackend
import sttp.model.Uri

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import cats.instances.future.*
import pureconfig.ConfigSource

@Description("Returns the status of a package by it's ID")
case class GetPackageById(
  @Description("The id of the package") package_id: String
) derives JsonSchemaTag, Decoder

object ExampleFunctionCall:

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  @main
  def run(): Unit =
    given ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    def requireEnv(name: String): String =
      sys.env.getOrElse(name, throw new IllegalStateException(s"Environment variable $name not set"))

    val llmModel: String    = requireEnv("DEFAULT_CHAT_MODEL")
    val llmVendorId: String = requireEnv("DEFAULT_CHAT_VENDOR")

    val vendors = ConfigSource.default.at("vendors").load[List[LLMVendor]] match
      case Left(error)  => throw new IllegalStateException(s"Failed to load config: $error")
      case Right(value) => value

    val llmVendor = vendors.find(_.id == llmVendorId).getOrElse(throw new IllegalStateException(s"Vendor $llmVendorId not found"))

    val openAiClient = OpenAiClient.forVendor(llmVendor, HttpClientFutureBackend())

    val getPackageById = Tool.function[GetPackageById]

    val function: GetPackageById => Future[String] = getPackageById =>
      Future:
        logger.info(s"Function called for ${getPackageById.package_id}")
        Thread.sleep(500)
        s"The package with id ${getPackageById.package_id} is currently in transit, estimated delivery is tomorrow."

    val getPackageByIdImpl = ToolImplementation.fromFunction[Future, GetPackageById](function)

    val request = ChatCompletionRequest(
      model = llmModel,
      messages = List(
        Message.system("You are an assistant chat bot that helps customers with their questions about their packages"),
        Message.user("Hi there!")
      ),
      tools = Some(List(getPackageById))
    )

    val response = Await.result(openAiClient.chatCompletion(request, Seq(getPackageByIdImpl)), 5.seconds)

    response match
      case Right(response) => println(response.firstMessageContent)
      case Left(error)     => println(error.message)
