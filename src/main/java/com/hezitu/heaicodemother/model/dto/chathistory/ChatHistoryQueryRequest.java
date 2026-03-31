package com.hezitu.heaicodemother.model.dto.chathistory;

import com.hezitu.heaicodemother.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史查询请求
 *
 * @author hezitu
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ChatHistoryQueryRequest extends PageRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 消息类型：user/ai/error
     */
    private String messageType;

    /**
     * 创建用户 id（管理员查询使用）
     */
    private Long userId;

    /**
     * 向前加载游标：传入当前最早一条消息的创建时间，加载比该时间更早的消息
     */
    private LocalDateTime beforeCreateTime;

    private static final long serialVersionUID = 1L;
}
