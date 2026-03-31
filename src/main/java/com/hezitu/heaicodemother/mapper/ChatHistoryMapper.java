package com.hezitu.heaicodemother.mapper;

import com.hezitu.heaicodemother.model.entity.ChatHistory;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话历史 映射层。
 *
 * @author hezitu
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

}
