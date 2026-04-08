package com.hezitu.heaicodemother.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@ConfigurationProperties(prefix = "ai.customer-support.chat-model")
public class CustomerSupportChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName = "deepseek-chat";

    private Integer maxTokens = 2048;

    private Boolean logRequests = true;

    private Boolean logResponses = true;

    private Integer maxRetries = 1;

    @Bean(name = "customerSupportChatModel")
    public ChatModel customerSupportChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .logRequests(Boolean.TRUE.equals(logRequests))
                .logResponses(Boolean.TRUE.equals(logResponses))
                .maxRetries(maxRetries)
                .build();
    }
}
