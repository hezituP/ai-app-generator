package com.hezitu.heaicodemother.service;

import com.hezitu.heaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.hezitu.heaicodemother.model.entity.ChatHistory;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 服务层。
 *
 * @author hezitu
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 保存一条对话消息
     *
     * @param appId       应用 id
     * @param message     消息内容
     * @param messageType 消息类型（user/ai/error）
     * @param userId      用户 id
     */
    void addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 分页查询某个应用的对话历史（最新 10 条，支持向前加载）
     * 以 createTime 作为游标，按创建时间降序排列，前端展示时需反转顺序
     *
     * @param appId            应用 id
     * @param beforeCreateTime 向前加载游标，传入当前最早消息的创建时间，为 null 则加载最新
     * @param pageSize         每页数量
     * @return 对话历史列表
     */
    List<ChatHistory> listChatHistory(Long appId, LocalDateTime beforeCreateTime, int pageSize);

    /**
     * 管理员分页查询所有应用的对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 分页结果
     */
    Page<ChatHistory> listAllChatHistoryByPageForAdmin(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 构造对话历史查询条件
     * 以 createTime 为游标，默认按创建时间降序排列
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 查询包装器
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 删除某个应用的所有对话历史（删除应用时关联删除）
     *
     * @param appId 应用 id
     */
    void deleteByAppId(Long appId);

    /**
     * 从数据库加载历史消息到 Redis 对话记忆
     * 以 appId_userId 作为 memoryId，将最近 maxCount 条 user/ai 消息写入 RedisChatMemoryStore
     *
     * @param appId    应用 id
     * @param userId   用户 id
     * @param maxCount 最多加载的消息条数
     * @return 实际加载的消息条数
     */
    int loadChatHistoryToMemory(Long appId, Long userId, int maxCount);
}
