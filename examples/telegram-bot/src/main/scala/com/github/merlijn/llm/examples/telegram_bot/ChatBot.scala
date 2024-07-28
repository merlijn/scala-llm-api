package com.github.merlijn.llm.examples.telegram_bot

import cats.data.EitherT
import cats.effect.Async
import cats.syntax.all.*
import cats.{Monad, Parallel}
import com.github.merlijn.llm.api.{ChatConfig, LLMVendor, OpenAiClient, ToolImplementation, dto}
import org.http4s.client.Client
import telegramium.bots.high.{Api, LongPollBot, Methods}
import telegramium.bots.{ChatIntId, Markdown, Message}

import scala.collection.immutable.Nil
import scala.util.{Failure, Success, Try}

class ChatBot[F[_]: Async: Parallel](
  api: Api[F],
  httpClient: Client[F],
  llmVendors: List[LLMVendor],
  defaultChatConfig: ChatConfig,
  chatStorage: ChatStorage[F],
  tools: List[ToolImplementation[F, ?]]
) extends LongPollBot[F](api):

  private val llmClients     = llmVendors.map(vendor => vendor.id -> OpenAiClient.forVendor[F](vendor, httpClient)).toMap
  private val logger         = org.slf4j.LoggerFactory.getLogger(getClass)
  private val errorMessage   = "An error occurred while processing your request. Please try again later."
  private val welcomeMessage = s"Welcome! Use /help to see available commands or start chatting :)"
  private val systemMessage  = dto.Message.system(defaultChatConfig.systemPrompt)
  private val help =
    """
      |Available commands:
      |
      |/state Show the current chat state
      |/clear Clear the chat history
      |/sysprompt <prompt> Updates the system prompt.
      |/temp <temperature> Set the temperature for the LLM
      |/models List the models available
      |/vendors List the vendors available
      |/use model <idx> Switch to a different model
      |/use vendor <vendor> Switch to a different model
      |/help Show this help message
      |""".stripMargin

  private def truncateHistory(history: List[dto.Message]): List[dto.Message] =
    if history.length > defaultChatConfig.maxHistory then
      dto.Message.system(defaultChatConfig.systemPrompt) :: history.drop(history.length - defaultChatConfig.maxHistory + 1)
    else
      history

  def getChatConfig(chatId: Long) =
    chatStorage.getChatConfig(chatId).map(_.getOrElse(defaultChatConfig))

  override def onMessage(msg: Message): F[Unit] =

    def getChatHistory(chatId: Long) =
      chatStorage.getMessages(chatId).map:
        case Nil      => List(systemMessage)
        case messages => truncateHistory(messages)

    def replyF(text: F[String]) =
      text.flatMap(reply)

    def reply(text: String): F[Unit] =
      api
        .execute(Methods.sendMessage(chatId = ChatIntId(msg.chat.id), text = text, parseMode = Some(Markdown)))
        .recoverWith(e => api.execute(Methods.sendMessage(chatId = ChatIntId(msg.chat.id), text = text, parseMode = None)))
        .void

    def replyT(response: EitherT[F, String, String]) =
      response.value.flatMap:
        case Left(error)    => reply(s"Exception processing request: $error")
        case Right(success) => reply(success)

    def setTemperature(t: Option[Double]) =
      replyF:
        for
          chatConfig <- getChatConfig(msg.chat.id)
          _          <- chatStorage.storeChatConfig(msg.chat.id, chatConfig.copy(temperature = t))
        yield s"Temperature set to $t"

    msg.text match
      case None =>
        logger.info(s"Received a message without text: ${msg}")
        Monad[F].unit
      case Some("/start") => reply(welcomeMessage)
      case Some("/help")  => reply(help)

      case Some(s"/use vendor $vendorId") =>
        llmVendors.find(_.id == vendorId) match
          case None => reply(s"Vendor $vendorId not found")
          case Some(vendor) =>
            replyF:
              for
                chatConfig <- getChatConfig(msg.chat.id)
                _          <- chatStorage.storeChatConfig(msg.chat.id, chatConfig.copy(vendorId = vendor.id, model = vendor.defaultModel))
              yield s"Switched to vendor: ${vendor.id}"

      case Some("/vendors") => reply(llmVendors.map(_.id).mkString("- ", "\n- ", ""))

      case Some(s"/use model $modelIdx") =>
        Try(modelIdx.toInt) match
          case Failure(_)              => reply("Model must be an number. See /models for available models")
          case Success(idx) if idx < 1 => reply(s"Model index $idx out of range. Must be 1 or higher")
          case Success(idx) =>
            replyT:
              for
                chatConfig <- EitherT.right(getChatConfig(msg.chat.id))
                models     <- EitherT(llmClients(chatConfig.vendorId).listModels()).leftMap(_.message)
                model      <- EitherT.fromOption[F](models.lift(idx - 1), s"Model index $idx out of range. Must be between 1 and ${models.length}. See /models for available models")
                _          <- EitherT.right(chatStorage.storeChatConfig(msg.chat.id, chatConfig.copy(model = model.id)))
              yield s"Now using model: ${model.id}"

      case Some("/state") =>
        replyF:
          for
            chatConfig  <- getChatConfig(msg.chat.id)
            chatHistory <- getChatHistory(msg.chat.id)
          yield s"""
                |Vendor: ${chatConfig.vendorId}
                |Model: ${chatConfig.model}
                |System Prompt: ${chatConfig.systemPrompt}
                |History: ${chatHistory.length - 1} messages
                |""".stripMargin

      case Some("/models") =>
        replyT:
          for
            chatConfig <- EitherT.right(getChatConfig(msg.chat.id))
            models     <- EitherT(llmClients(chatConfig.vendorId).listModels()).leftMap(_.message)
          yield models.map(m => m.id).zipWithIndex.map((id, idx) => s"${idx + 1}. $id").mkString("\n").stripLineEnd

      case Some("/clear") =>
        replyF:
          for
            chatConfig <- getChatConfig(msg.chat.id)
            _          <- chatStorage.setHistory(msg.chat.id, List(dto.Message.system(chatConfig.systemPrompt)))
          yield "Chat history cleared"

      case Some("/temp none") =>
        setTemperature(None)

      case Some(s"/temp $temp") =>
        Try(temp.toDouble) match
          case Failure(_)                     => reply("Temperature must be a number")
          case Success(t) if t < 0.1 || t > 1 => reply("Temperature must be between 0.1 and 1")
          case Success(t)                     => setTemperature(Some(t))

      case Some(s"/sysprompt $prompt") =>
        replyF:
          for
            chatConfig  <- getChatConfig(msg.chat.id)
            chatHistory <- getChatHistory(msg.chat.id)
            _           <- chatStorage.storeChatConfig(msg.chat.id, chatConfig.copy(systemPrompt = prompt))
          yield "System prompt updated, to use this prompt you need to /clear the chat history."

      case Some(s"/$other") =>
        reply(s"Unknown command: $other")

      case Some(userMessage) =>
        logger.info(s"Processing message from ${msg.from.map(_.firstName)}")

        for
          chatConfig  <- getChatConfig(msg.chat.id)
          chatHistory <- getChatHistory(msg.chat.id)
          chatRequest = dto.ChatCompletionRequest(
            model = chatConfig.model,
            messages = chatHistory :+ dto.Message.user(userMessage),
            stream = None,
            chatConfig.temperature,
            chatConfig.maxTokens,
            tools = Option.when(tools.nonEmpty)(tools.map(_.spec)),
            tool_choice = Option.when(tools.nonEmpty)("auto")
          )
          _ <- llmClients(chatConfig.vendorId).chatCompletion(chatRequest, tools).flatMap:
            case Right(response) =>
              response.firstMessageContent match
                case None => reply(errorMessage)
                case Some(content) =>
                  val newHistory = chatHistory ::: List(dto.Message.user(userMessage), dto.Message.assistant(content))
                  reply(content) >> chatStorage.setHistory(msg.chat.id, newHistory)
            case Left(error) =>
              logger.error(s"LLM Request returned an error: ${error}")
              reply(errorMessage)
        yield ()
