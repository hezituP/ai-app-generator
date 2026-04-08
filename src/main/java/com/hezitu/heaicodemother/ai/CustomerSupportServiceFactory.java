package com.hezitu.heaicodemother.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerSupportServiceFactory {

    @Resource(name = "customerSupportChatModel")
    private ChatModel customerSupportChatModel;

    @Bean
    public CustomerSupportService customerSupportService() {
        return AiServices.builder(CustomerSupportService.class)
                .chatModel(customerSupportChatModel)
                .build();
    }
}
