package com.github.merlijn.llm.examples.function_call

import com.github.merlijn.llm.api.dto.*
import com.github.merlijn.llm.api.schema.{Description, JsonSchemaTag}
import com.github.merlijn.llm.api.{OpenAiClient, ToolImplementation}
import io.circe.Decoder
import sttp.client3.HttpClientFutureBackend
import sttp.model.Uri

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import cats.instances.future._

@Description("Returns the status of a package by it's ID")
case class GetPackageById(
  @Description("The id of the package") package_id: String
) derives JsonSchemaTag, Decoder

object ExampleFunctionCall:

  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  @main
  def run(): Unit =
    given ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    val llmToken = sys.env.get("LLM_TOKEN")
    val llmBaseUrl = sys.env.getOrElse("LLM_BASE_URL", "https://api.openai.com/v1")
    val llmModel = sys.env.getOrElse("LLM_MODEL", "gpt-4o")

    val openAiClient = new OpenAiClient(
      apiToken = llmToken,
      backend = HttpClientFutureBackend(),
      baseUri = Uri.parse(llmBaseUrl).getOrElse(throw new IllegalStateException("Invalid base URL"))
    )

    val getPackageById = Tool.function[GetPackageById]("Get the status of a package by it's ID")

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
        Message.user("Do you know what the status of my package is? The id is 237293GR"),
      ),
      tools = Some(List(getPackageById)),
    )

    val response = Await.result(openAiClient.chatCompletion(request, Seq(getPackageByIdImpl)), 5.seconds)

    response match
      case Right(response) => println(response.firstMessageContent)
      case Left(error)     => println(error.message)
