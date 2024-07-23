package com.github.merlijn.llm.examples.telegram_bot

import cats.data.EitherT
import cats.{Monad, Parallel}
import cats.effect.Async
import com.github.merlijn.llm.api.{OpenAiClient, ToolImplementation, dto}
import telegramium.bots.{ChatIntId, Markdown, Message, ParseMode}
import telegramium.bots.high.{Api, LongPollBot, Methods}
import cats.syntax.all.*

import scala.collection.immutable.Nil

class ChatBot[F[_]: Async: Parallel](
  api: Api[F],
  llmClients: Map[String, OpenAiClient[F]],
  defaultChatConfig: ChatConfig,
  chatStorage: ChatStorage[F],
  tools: List[ToolImplementation[F, ?]]
) extends LongPollBot[F](api):

  private val logger         = org.slf4j.LoggerFactory.getLogger(getClass)
  private val errorMessage   = "An error occurred while processing your request. Please try again later."
  private val welcomeMessage = s"Welcome! You are talking to ${defaultChatConfig.model} from ${defaultChatConfig.vendorId}. Please start chatting :). Use /help to see available commands."
  private val systemMessage  = dto.Message.system(defaultChatConfig.systemPrompt)
  private val help =
    """
      | Available commands:
      |
      | /list List the models available
      | /use <model> Switch to a different model
      | /vendors List the vendors available
      | /vendor <vendor> Switch to a different vendor
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

    def reply(text: String) =
      api.execute(Methods.sendMessage(chatId = ChatIntId(msg.chat.id), text = text, parseMode = Some(Markdown))).void

    def replyWith(response: EitherT[F, String, String]) =
      response.value.flatMap:
        case Left(error)    => reply(s"Exception processing request: $error")
        case Right(success) => reply(success)

    msg.text match
      case None =>
        logger.info(s"Received a message without text: ${msg}")
        Monad[F].unit
      case Some("/start") => reply(welcomeMessage)
      case Some("/help")  => reply(help)
      case Some(s"/vendor $vendor") =>
        if llmClients.contains(vendor) then
          for
            chatConfig <- getChatConfig(msg.chat.id)
            _          <- chatStorage.storeChatConfig(msg.chat.id, chatConfig.copy(vendorId = vendor))
            _          <- reply(s"Switched to vendor: $vendor")
          yield ()
        else
          reply(s"Vendor $vendor not found")

      case Some("/vendors") => reply(llmClients.keys.mkString("- ", "\n- ", ""))
      case Some(s"/use $model") =>
        val result =
          for
            chatConfig <- EitherT.right(getChatConfig(msg.chat.id))
            models     <- EitherT(llmClients(chatConfig.vendorId).listModels()).leftMap(_.message)
            _          <- EitherT.cond(models.exists(_.id == model), (), "Model not found")
            _          <- EitherT.right(chatStorage.storeChatConfig(msg.chat.id, chatConfig.copy(model = model)))
          yield s"Now using model: $model"

        replyWith(result)

      case Some("/list") =>
        val result =
          for
            chatConfig <- EitherT.right(getChatConfig(msg.chat.id))
            models     <- EitherT(llmClients(chatConfig.vendorId).listModels()).leftMap(_.message)
          yield models.map(m => m.id).mkString("- ", "\n- ", "")

        replyWith(result)

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
