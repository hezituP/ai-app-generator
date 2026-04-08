package com.hezitu.heaicodemother.ai;

import dev.langchain4j.service.SystemMessage;

public interface AppChatIntentRoutingService {

    @SystemMessage(fromResource = "prompt/app-chat-intent-routing-system-prompt.txt")
    String routeIntent(String userMessage);
}
