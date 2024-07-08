# Basic Scala API for Open AI compatible LLM api's

This is a simple API for Open AI compatible LLM API's. 

It is not meant to be used in production, it is incomplete and meant to be used for learning purposes or as a starting point for your own system.

I recommend copy/pasting the code into your own project and modifying it to suit your needs.

Dependencies are: 
- [Circe](https://circe.github.io/circe/) for JSON encoding/decoding
- [STTP](https://sttp.softwaremill.com/en/latest/) for doing HTTP requests
- [Scala json schema](https://github.com/andyglow/scala-jsonschema) for generating JSON schema's from case classes

These could be replaced with other libraries relatively easily.

Look in the `examples` directory for example usages. There are 2:

1. [A simple request response chat](examples/simple-chat/src/main/scala/com/github/merlijn/llm/examples/chat/ExampleSimpleChatResponse.scala)
2. [A basic Telegram bot](examples/telegram-bot/src/main/scala/com/github/merlijn/llm/examples/telegram_bot/ExampleTelegramBot.scala)
3. [Function calling](examples/function-call/src/main/scala/com/github/merlijn/llm/examples/function_call/ExampleFunctionCall.scala)



