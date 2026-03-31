package com.hezitu.heaicodemother.mapper;

import com.mybatisflex.core.BaseMapper;
import com.hezitu.heaicodemother.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 映射层。
 *
 * @author hezitu
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
