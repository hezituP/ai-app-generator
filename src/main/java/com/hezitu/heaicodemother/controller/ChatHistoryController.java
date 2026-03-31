package com.hezitu.heaicodemother.controller;

import com.hezitu.heaicodemother.annotation.AuthCheck;
import com.hezitu.heaicodemother.common.BaseResponse;
import com.hezitu.heaicodemother.common.ResultUtils;
import com.hezitu.heaicodemother.constant.UserConstant;
import com.hezitu.heaicodemother.exception.BusinessException;
import com.hezitu.heaicodemother.exception.ErrorCode;
import com.hezitu.heaicodemother.exception.ThrowUtils;
import com.hezitu.heaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.hezitu.heaicodemother.model.entity.App;
import com.hezitu.heaicodemother.model.entity.ChatHistory;
import com.hezitu.heaicodemother.model.entity.User;
import com.hezitu.heaicodemother.service.AppService;
import com.hezitu.heaicodemother.service.ChatHistoryService;
import com.hezitu.heaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 控制层。
 *
 * @author hezitu
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    // ==================== 用户接口 ====================

    /**
     * 加载应用对话历史（最新 10 条，支持向前加载更多）
     * 仅应用创建者和管理员可访问
     *
     * @param appId            应用 id
     * @param beforeCreateTime 向前加载游标，传入当前最早消息的创建时间；首次加载传 null
     * @param pageSize         每页数量，默认 10，最大 50
     * @param request          请求
     * @return 对话历史列表（按时间降序，前端展示时需反转）
     */
    @GetMapping("/list")
    public BaseResponse<List<ChatHistory>> listChatHistory(
            @RequestParam Long appId,
            @RequestParam(required = false) LocalDateTime beforeCreateTime,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 校验权限：仅应用创建者或管理员可查看
        User loginUser = userService.getLoginUser(request);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isOwner = app.getUserId().equals(loginUser.getId());
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该应用的对话历史");
        }
        List<ChatHistory> list = chatHistoryService.listChatHistory(appId, beforeCreateTime, pageSize);
        return ResultUtils.success(list);
    }

    // ==================== 管理员接口 ====================

    /**
     * 管理员分页查询所有应用的对话历史（按时间降序）
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 分页结果
     */
    @PostMapping("/admin/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> adminPageChatHistory(
            @RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Page<ChatHistory> page = chatHistoryService.listAllChatHistoryByPageForAdmin(chatHistoryQueryRequest);
        return ResultUtils.success(page);
    }
}
