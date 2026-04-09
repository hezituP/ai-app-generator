package com.hezitu.heaicodemother.config;
import com.hezitu.heaicodemother.monitor.AiModelMonitorListener;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;

@Configuration
@Data
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
public class ReasoningStreamingChatModelConfig {

    @Resource
    private AiModelMonitorListener aiModelMonitorListener;

    private String baseUrl;

    private String apiKey;

    private String modelName = "deepseek-reasoner";

    private Integer maxTokens = 32768;

    private Double temperature = 0.1;

    private boolean logRequests = true;

    private boolean logResponses = true;

    @Bean
    @Scope("prototype")
    public StreamingChatModel reasoningStreamingChatModel(){
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .listeners(List.of(aiModelMonitorListener))
                .build();

    }
}
