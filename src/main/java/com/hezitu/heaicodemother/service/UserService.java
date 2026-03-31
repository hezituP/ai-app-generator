package com.hezitu.heaicodemother.service;

import com.hezitu.heaicodemother.model.dto.user.UserQueryRequest;
import com.hezitu.heaicodemother.model.vo.LoginUserVO;
import com.hezitu.heaicodemother.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.hezitu.heaicodemother.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author hezitu
 */
public interface UserService extends IService<User> {
    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 获取脱敏的用户数据
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 用户登录
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取脱敏用户信息
     *
     * @param user 用户实体
     * @return 脱敏后的用户视图
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏用户信息列表
     *
     * @param userList 用户实体列表
     * @return 脱敏后的用户视图列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 根据查询条件来构造数据查询参数
     *
     * @param userQueryRequest 查询请求
     * @return 查询包装器
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 密码加密
     * @param userPassword
     * @return
     */
    String getEncryptPassword (String userPassword);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);
}

