package com.github.merlijn.llm

package object api:
  def camelToSnake(camelCase: String): String =
    camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase
