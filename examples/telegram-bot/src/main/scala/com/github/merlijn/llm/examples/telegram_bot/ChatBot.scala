package com.github.merlijn.llm.examples.telegram_bot

import cats.data.EitherT
import cats.effect.Async
import cats.syntax.all.*
import cats.{Monad, Parallel}
import com.github.merlijn.llm.api.{LLMVendor, OpenAiClient, ToolImplementation, dto}
import sttp.client3.SttpBackend
import telegramium.bots.high.{Api, LongPollBot, Methods}
import telegramium.bots.{ChatIntId, Markdown, Message}

import scala.collection.immutable.Nil
import scala.util.{Failure, Success, Try}

class ChatBot[F[_]: Async: Parallel](
  api: Api[F],
  sttpBackend: SttpBackend[F, ?],
  llmVendors: List[LLMVendor],
  defaultChatConfig: ChatConfig,
  chatStorage: ChatStorage[F],
  tools: List[ToolImplementation[F, ?]]
) extends LongPollBot[F](api):

  private val llmClients     = llmVendors.map(vendor => vendor.id -> OpenAiClient.forVendor[F](vendor, sttpBackend)).toMap
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
      |/models List the models available
      |/vendors List the vendors available
      |/set model <idx> Switch to a different model
      |/set vendor <vendor> Switch to a different model
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
        .void

    def replyT(response: EitherT[F, String, String]) =
      response.value.flatMap:
        case Left(error)    => reply(s"Exception processing request: $error")
        case Right(success) => reply(success)

    msg.text match
      case None =>
        logger.info(s"Received a message without text: ${msg}")
        Monad[F].unit
      case Some("/start") => reply(welcomeMessage)
      case Some("/help")  => reply(help)

      case Some(s"/set vendor $vendorId") =>
        llmVendors.find(_.id == vendorId) match
          case None => reply(s"Vendor $vendorId not found")
          case Some(vendor) =>
            replyF:
              for
                chatConfig <- getChatConfig(msg.chat.id)
                _          <- chatStorage.storeChatConfig(msg.chat.id, chatConfig.copy(vendorId = vendor.id, model = vendor.defaultModel))
              yield s"Switched to vendor: $vendor"

      case Some("/vendors") => reply(llmVendors.map(_.id).mkString("- ", "\n- ", ""))

      case Some(s"/set model $modelIdx") =>
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
          yield models.map(m => m.id).zipWithIndex.map((id, idx) => s"${idx+1}. $id").mkString("\n").stripLineEnd

      case Some("/clear") =>
        replyF:
          for
            chatConfig <- getChatConfig(msg.chat.id)
            _          <- chatStorage.setHistory(msg.chat.id, List(dto.Message.system(chatConfig.systemPrompt)))
          yield "Chat history cleared"

      case Some(s"/sysprompt $prompt") =>
        replyF:
          for
            chatConfig  <- getChatConfig(msg.chat.id)
            chatHistory <- getChatHistory(msg.chat.id)
            _           <- chatStorage.setHistory(msg.chat.id, dto.Message.system(prompt) :: chatHistory.tail)
          yield "System prompt updated"

      case Some(userMessage) =>
        logger.info(s"Processing message from ${msg.from.map(_.firstName)}")

        for
          chatConfig  <- getChatConfig(msg.chat.id)
          chatHistory <- getChatHistory(msg.chat.id)
          chatRequest = dto.ChatCompletionRequest(
            model = chatConfig.model,
            messages = chatHistory :+ dto.Message.user(userMessage),
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
