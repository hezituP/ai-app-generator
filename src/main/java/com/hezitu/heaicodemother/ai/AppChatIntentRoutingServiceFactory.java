package com.hezitu.heaicodemother.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppChatIntentRoutingServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Bean
    public AppChatIntentRoutingService appChatIntentRoutingService() {
        return AiServices.builder(AppChatIntentRoutingService.class)
                .chatModel(chatModel)
                .build();
    }
}
