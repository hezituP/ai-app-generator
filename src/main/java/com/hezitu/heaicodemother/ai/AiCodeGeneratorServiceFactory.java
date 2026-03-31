package com.hezitu.heaicodemother.ai;

import com.hezitu.heaicodemother.exception.BusinessException;
import com.hezitu.heaicodemother.exception.ErrorCode;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import com.hezitu.heaicodemother.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务创建工厂
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 根据 appId 获取服务（兼容调用，无 userId 时不预热记忆）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML, null);
    }

    /**
     * 根据 appId 和代码生成类型获取服务（无 userId 时不预热记忆）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        return getAiCodeGeneratorService(appId, codeGenType, null);
    }

    /**
     * 根据 appId、代码生成类型和 userId 获取服务，自动预热 Redis 对话记忆
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, Long userId) {
        return createAiCodeGeneratorService(appId, codeGenType, userId);
    }

    /**
     * 创建 AI 服务实例（基于 Redis 持久化对话记忆）
     * 若 userId 不为空，则先从数据库加载历史消息预热 Redis 记忆，避免 TTL 过期后上下文丢失
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, Long userId) {
        // 若 Redis 记忆已过期，从数据库恢复历史消息
        if (userId != null && appId > 0) {
            try {
                int loaded = chatHistoryService.loadChatHistoryToMemory(appId, userId, 20);
                log.info("预热对话记忆，appId: {}, userId: {}, 加载条数: {}", appId, userId, loaded);
            } catch (Exception e) {
                log.warn("预热对话记忆失败，appId: {}, userId: {}, 原因: {}", appId, userId, e.getMessage());
            }
        }
        return switch (codeGenType) {
            case HTML, MULTI_FILE -> AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(streamingChatModel)
                    // 根据 memoryId 构造独立的对话记忆，持久化至 Redis
                    .chatMemoryProvider(memoryId -> MessageWindowChatMemory
                            .builder()
                            .id(memoryId)
                            .chatMemoryStore(redisChatMemoryStore)
                            .maxMessages(20)
                            .build())
                    .build();
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }

    /**
     * 注册默认 AI 服务 Bean
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0);
    }
}
