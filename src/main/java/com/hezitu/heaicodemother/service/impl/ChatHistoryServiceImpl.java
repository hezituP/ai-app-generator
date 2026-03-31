package com.hezitu.heaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hezitu.heaicodemother.exception.BusinessException;
import com.hezitu.heaicodemother.exception.ErrorCode;
import com.hezitu.heaicodemother.exception.ThrowUtils;
import com.hezitu.heaicodemother.mapper.ChatHistoryMapper;
import com.hezitu.heaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.hezitu.heaicodemother.model.entity.ChatHistory;
import com.hezitu.heaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.hezitu.heaicodemother.service.ChatHistoryService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史 服务层实现。
 *
 * @author hezitu
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>
        implements ChatHistoryService {

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Override
    public int loadChatHistoryToMemory(Long appId, Long userId, int maxCount) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        if (maxCount <= 0) {
            maxCount = 20;
        }
        // 1. 从数据库查询最近 maxCount 条历史（按时间降序，再反转为正序）
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .in("messageType", ChatHistoryMessageTypeEnum.USER.getValue(),
                        ChatHistoryMessageTypeEnum.AI.getValue())
                .orderBy("createTime", false)
                .limit(maxCount);
        List<ChatHistory> historyList = this.list(queryWrapper);
        if (historyList == null || historyList.isEmpty()) {
            return 0;
        }
        // 2. 反转为时间正序
        historyList = new ArrayList<>(historyList);
        java.util.Collections.reverse(historyList);
        // 3. 将历史消息转换为 langchain4j ChatMessage 列表
        List<ChatMessage> messages = new ArrayList<>();
        for (ChatHistory history : historyList) {
            ChatHistoryMessageTypeEnum typeEnum =
                    ChatHistoryMessageTypeEnum.getEnumByValue(history.getMessageType());
            if (typeEnum == null) {
                continue;
            }
            switch (typeEnum) {
                case USER -> messages.add(UserMessage.from(history.getMessage()));
                case AI -> messages.add(AiMessage.from(history.getMessage()));
                default -> log.warn("跳过不支持的消息类型: {}", history.getMessageType());
            }
        }
        // 4. 以 appId_userId 作为 memoryId，写入 RedisChatMemoryStore
        String memoryId = appId + "_" + userId;
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(maxCount)
                .build();
        chatMemory.clear();
        messages.forEach(chatMemory::add);
        log.info("已加载 {} 条历史消息到 Redis 记忆，memoryId: {}", messages.size(), memoryId);
        return messages.size();
    }


    @Override
    public void addChatMessage(Long appId, String message, String messageType, Long userId) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        // 校验消息类型合法性
        ChatHistoryMessageTypeEnum typeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(typeEnum == null, ErrorCode.PARAMS_ERROR, "消息类型非法");
        // 构建并保存
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setAppId(appId);
        chatHistory.setMessage(message);
        chatHistory.setMessageType(messageType);
        chatHistory.setUserId(userId);
        boolean saved = this.save(chatHistory);
        if (!saved) {
            log.error("保存对话历史失败，appId: {}, messageType: {}", appId, messageType);
        }
    }

    @Override
    public List<ChatHistory> listChatHistory(Long appId, LocalDateTime beforeCreateTime, int pageSize) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        if (pageSize <= 0 || pageSize > 50) {
            pageSize = 10;
        }
        // 构造查询条件，复用 getQueryWrapper
        ChatHistoryQueryRequest req = new ChatHistoryQueryRequest();
        req.setAppId(appId);
        req.setBeforeCreateTime(beforeCreateTime);
        req.setPageSize(pageSize);
        QueryWrapper queryWrapper = getQueryWrapper(req);
        queryWrapper.limit(pageSize);
        return this.list(queryWrapper);
    }

    @Override
    public Page<ChatHistory> listAllChatHistoryByPageForAdmin(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        if (chatHistoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        int pageNum = chatHistoryQueryRequest.getPageNum();
        int pageSize = chatHistoryQueryRequest.getPageSize();
        QueryWrapper queryWrapper = getQueryWrapper(chatHistoryQueryRequest);
        return this.page(Page.of(pageNum, pageSize), queryWrapper);
    }

    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        if (chatHistoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long appId = chatHistoryQueryRequest.getAppId();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime beforeCreateTime = chatHistoryQueryRequest.getBeforeCreateTime();
        // 构造查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("messageType", messageType)
                .eq("userId", userId);
        // 以 createTime 作为游标：加载比 beforeCreateTime 更早的消息
        if (beforeCreateTime != null) {
            queryWrapper.lt("createTime", beforeCreateTime);
        }
        // 默认按创建时间降序排列
        queryWrapper.orderBy("createTime", false);
        return queryWrapper;
    }

    @Override
    public void deleteByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            return;
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        this.remove(queryWrapper);
        log.info("已删除应用 {} 的所有对话历史", appId);
    }
}
