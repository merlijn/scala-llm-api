package com.github.merlijn.llm.examples.telegram_bot

import cats.{Monad, Parallel}
import cats.effect.Async
import com.github.merlijn.llm.api.{OpenAiClient, dto}
import telegramium.bots.{ChatIntId, Markdown, Message, ParseMode}
import telegramium.bots.high.{Api, LongPollBot, Methods}
import cats.syntax.all.*

import scala.collection.immutable.Nil

case class ChatConfig(
  llmModel: String,
  llmSystemPrompt: String,
  maxHistory: Int = 100,
  parseMode: Option[ParseMode] = Markdown.some,
  temperature: Option[Double] = Some(0.8),
  max_tokens: Option[Int] = Some(1500)
)

class ChatBot[F[_]: Async: Parallel](
  api: Api[F],
  llmClient: OpenAiClient[F],
  chatConfig: ChatConfig,
  chatStorage: ChatStorage[F]
) extends LongPollBot[F](api):

  private val logger         = org.slf4j.LoggerFactory.getLogger(getClass)
  private val errorMessage   = "An error occurred while processing your request. Please try again later."
  private val welcomeMessage = s"Welcome! You are talking to ${chatConfig.llmModel}. Please start chatting :)"
  private val systemMessage  = dto.Message.system(chatConfig.llmSystemPrompt)

  private def truncateHistory(history: List[dto.Message]): List[dto.Message] =
    if history.length > chatConfig.maxHistory then
      dto.Message.system(chatConfig.llmSystemPrompt) :: history.drop(history.length - chatConfig.maxHistory + 1)
    else
      history

  override def onMessage(msg: Message): F[Unit] =

    def getChatHistory(chatId: Long) =
      chatStorage.getMessages(chatId).map:
        case Nil      => List(systemMessage)
        case messages => truncateHistory(messages)

    def reply(text: String) =
      api.execute(Methods.sendMessage(chatId = ChatIntId(msg.chat.id), text = text, parseMode = chatConfig.parseMode)).void

    msg.text match
      case None =>
        logger.info(s"Received a message without text: ${msg}")
        Monad[F].unit
      case Some("/start") => reply(welcomeMessage)
      case Some("/list") =>
        llmClient.listModels().flatMap:
          case Right(models) =>
            val modelList = models.map(m => m.id).mkString("- ", "\n- ", "")
            reply(modelList)
          case Left(error) =>
            logger.error(s"LLM Request returned an error: ${error}")
            reply("Error while fetching models")

      case Some(userMessage) =>
        logger.info(s"Processing message from ${msg.from.map(_.firstName)}")

        for
          chatHistory <- getChatHistory(msg.chat.id)
          chatRequest = dto.ChatCompletionRequest(
            model = chatConfig.llmModel,
            messages = chatHistory :+ dto.Message.user(userMessage),
            chatConfig.temperature,
            chatConfig.max_tokens
          )
          _ <- llmClient.chatCompletion(chatRequest).flatMap:
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
