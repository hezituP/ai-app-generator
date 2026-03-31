package com.hezitu.heaicodemother.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 表示脱敏后的用户登录信息
 */
@Data
public class LoginUserVO implements Serializable {

    /**
     * 用户id
     */
    private Long id;
    /**
     * 用户昵称
     */
    private String userName;
    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户头像
     */
    private String userAvatar;
    /**
     * 用户简介
     */
    private String userProfile;
    /**
     * 用户角色：user/admin/ban
     */
    private String userRole;
    /**
     * 创建时间
     */
    private String createTime;
    /**
     * 更新时间
     */
    private String updateTime;

    public static final long serialVersionUID = 1L;

}
