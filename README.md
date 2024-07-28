# Basic Scala API for Open AI compatible LLM api's

This is a simple API for Open AI compatible LLM API's. 

It is not meant to be used in production, it is incomplete and meant to be used for learning purposes or as a starting point for your own system.

I recommend copy/pasting the code into your own project and modifying it to suit your needs.

Dependencies are: 
- [Circe](https://circe.github.io/circe/) for JSON encoding/decoding
- [HTTP4S](https://http4s.org/) for doing HTTP requests

These could be replaced with other libraries relatively easily.

Note: json schema derivation is very limited and does not support recursive types.

Look in the `examples` directory for example usages:

1. [A simple request response chat](examples/simple-chat/src/main/scala/com/github/merlijn/llm/examples/chat/ExampleSimpleChatResponse.scala)
3. [Function calling](examples/function-call/src/main/scala/com/github/merlijn/llm/examples/function_call/ExampleFunctionCall.scala)
2. [A basic Telegram bot](examples/telegram-bot/src/main/scala/com/github/merlijn/llm/examples/telegram_bot/ChatBotApp.scala) (Uses [Telegramium](https://github.com/apimorphism/telegramium) for the Telegram bot API)

Usage:

```bash
# 1. copy the example env file
cp .env.example .env

# 2. edit the .env file to include your API key

# 3. run an example
sbt 'telegramBot/run'
```



