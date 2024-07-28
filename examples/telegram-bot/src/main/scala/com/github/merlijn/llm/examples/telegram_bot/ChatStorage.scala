package com.github.merlijn.llm.examples.telegram_bot

import cats.Monad
import com.github.merlijn.llm.api.ChatConfig
import com.github.merlijn.llm.api.dto.Message

class ChatStorage[F[_]: Monad](initialState: Map[Long, List[Message]] = Map.empty):

  private var state: Map[Long, List[Message]]   = initialState
  private var chatConfig: Map[Long, ChatConfig] = Map.empty

  def setHistory(chatId: Long, messages: List[Message]): F[Unit] =
    Monad[F].point:
      state = state.updated(chatId, messages)

  def getChatConfig(chatId: Long): F[Option[ChatConfig]] =
    Monad[F].point:
      chatConfig.get(chatId)

  def storeChatConfig(chatId: Long, config: ChatConfig): F[Unit] =
    Monad[F].point:
      chatConfig = chatConfig.updated(chatId, config)

  def getMessages(chatId: Long): F[List[Message]] =
    Monad[F].point:
      state.getOrElse(chatId, Nil)
