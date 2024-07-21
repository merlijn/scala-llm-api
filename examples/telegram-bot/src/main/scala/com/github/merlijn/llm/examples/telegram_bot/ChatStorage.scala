package com.github.merlijn.llm.examples.telegram_bot

import cats.Monad
import com.github.merlijn.llm.api.dto.Message

class ChatStorage[F[_]: Monad](initialState: Map[Long, List[Message]] = Map.empty):

  private var state: Map[Long, List[Message]] = initialState

  def setHistory(chatId: Long, messages: List[Message]): F[Unit] =
    Monad[F].point:
      state = state.updated(chatId, messages)

  def getMessages(chatId: Long): F[List[Message]] =
    Monad[F].point(state.getOrElse(chatId, Nil))
