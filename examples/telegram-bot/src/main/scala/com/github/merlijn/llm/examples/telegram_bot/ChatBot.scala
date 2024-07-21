package com.github.merlijn.llm.examples.telegram_bot

import cats.{Monad, Parallel}
import cats.effect.Async
import com.github.merlijn.llm.api.{OpenAiClient, dto}
import telegramium.bots.{ChatIntId, Message}
import telegramium.bots.high.{Api, LongPollBot, Methods}
import cats.syntax.all.*

class ChatBot[F[_]: Async: Parallel](api: Api[F], chatStorage: ChatStorage[F], llmClient: OpenAiClient[F], llmModel: String, llmSystemPrompt: String) extends LongPollBot[F](api):

  private val logger       = org.slf4j.LoggerFactory.getLogger(getClass)
  private val errorMessage = "An error occurred while processing your request. Please try again later."
  private val maxHistory   = 100

  def truncateHistory(history: List[dto.Message]): List[dto.Message] =
    if history.length > maxHistory then
      dto.Message.system(llmSystemPrompt) :: history.drop(history.length - maxHistory + 1)
    else
      history

  override def onMessage(msg: Message): F[Unit] =

    def getChatHistory(chatId: Long) =
      chatStorage.getMessages(chatId).map:
        case Nil      => List(dto.Message.system(llmSystemPrompt))
        case messages => truncateHistory(messages)

    msg.text match
      case None => Monad[F].unit
      case Some(userMessage) =>
        def reply(history: List[dto.Message], response: String) =
          api.execute(Methods.sendMessage(chatId = ChatIntId(msg.chat.id), text = response)) >>
            chatStorage.setHistory(msg.chat.id, history ::: List(dto.Message.user(userMessage), dto.Message.assistant(response)))

        for {
          chatHistory <- getChatHistory(msg.chat.id)
          chatRequest = dto.ChatCompletionRequest(
            model = llmModel,
            messages = chatHistory :+ dto.Message.user(userMessage),
            temperature = Some(0.8),
            max_tokens = Some(1500)
          )
          chatResponse <- llmClient.chatCompletion(chatRequest).flatMap:
            case Right(response) =>
              response.firstMessageContent match
                case None          => reply(chatHistory, errorMessage)
                case Some(content) => reply(chatHistory, content)
            case Left(error) =>
              logger.error(s"LLM Request returned an error: ${error}")
              reply(chatHistory, errorMessage)
        } yield ()
