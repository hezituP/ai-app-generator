package com.hezitu.heaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hezitu.heaicodemother.ai.AppChatIntentRoutingService;
import com.hezitu.heaicodemother.ai.CustomerSupportService;
import com.hezitu.heaicodemother.constant.AppConstant;
import com.hezitu.heaicodemother.core.AiCodeGeneratorFacade;
import com.hezitu.heaicodemother.core.builder.VueProjectBuilder;
import com.hezitu.heaicodemother.exception.BusinessException;
import com.hezitu.heaicodemother.exception.ErrorCode;
import com.hezitu.heaicodemother.exception.ThrowUtils;
import com.hezitu.heaicodemother.langgraph4j.CodeGenWorkflow;
import com.hezitu.heaicodemother.mapper.AppMapper;
import com.hezitu.heaicodemother.model.dto.app.AppAddRequest;
import com.hezitu.heaicodemother.model.dto.app.AppQueryRequest;
import com.hezitu.heaicodemother.model.entity.ChatHistory;
import com.hezitu.heaicodemother.model.entity.App;
import com.hezitu.heaicodemother.model.entity.User;
import com.hezitu.heaicodemother.model.enums.AppChatIntentEnum;
import com.hezitu.heaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
import com.hezitu.heaicodemother.model.vo.AppProjectFileVO;
import com.hezitu.heaicodemother.model.vo.AppProjectSnapshotVO;
import com.hezitu.heaicodemother.model.vo.AppVO;
import com.hezitu.heaicodemother.model.vo.UserVO;
import com.hezitu.heaicodemother.service.AppService;
import com.hezitu.heaicodemother.service.ChatHistoryService;
import com.hezitu.heaicodemother.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.mybatisflex.core.paginate.Page;

@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;

    @Resource
    private UserService userService;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private CodeGenWorkflow codeGenWorkflow;

    @Resource
    private AppChatIntentRoutingService appChatIntentRoutingService;

    @Resource
    private CustomerSupportService customerSupportService;

    @Override
    public Flux<AgentStreamEvent> chatToGenCode(Long appId, String message, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "Message cannot be empty");
        App app = validateAndGetOwnedApp(appId, loginUser);
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid code generation type");
        }
        chatHistoryService.addChatMessage(appId, message,
                ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        AppChatIntentEnum chatIntent = routeChatIntent(message);
        if (chatIntent == AppChatIntentEnum.CUSTOMER_REPLY) {
            return replyToCustomer(app, message, loginUser);
        }
        StringBuilder aiResponseBuilder = new StringBuilder();
        return codeGenWorkflow.executeWorkflowStream(appId, loginUser.getId(), message, codeGenTypeEnum)
                .doOnNext(event -> {
                    if (StrUtil.isNotBlank(event.getMessage())
                            && ("assistant".equals(event.getType()) || "result".equals(event.getType()))) {
                        if (aiResponseBuilder.length() > 0) {
                            aiResponseBuilder.append("\n");
                        }
                        aiResponseBuilder.append(event.getMessage());
                    }
                })
                .doOnComplete(() -> {
                    String aiResponse = aiResponseBuilder.toString().trim();
                    if (StrUtil.isNotBlank(aiResponse)) {
                    chatHistoryService.addChatMessage(appId, aiResponse,
                                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    }
                })
                .doOnError(e -> {
                    log.error("AI code generation failed, appId: {}, error: {}", appId, e.getMessage(), e);
                    chatHistoryService.addChatMessage(appId,
                            "AI generation failed: " + e.getMessage(),
                            ChatHistoryMessageTypeEnum.ERROR.getValue(), loginUser.getId());
                });
    }

    private AppChatIntentEnum routeChatIntent(String message) {
        if (StrUtil.isBlank(message)) {
            return AppChatIntentEnum.CODE_GEN;
        }
        try {
            AppChatIntentEnum intent = parseIntent(appChatIntentRoutingService.routeIntent(message));
            return intent == null ? fallbackIntent(message) : intent;
        } catch (Exception e) {
            log.warn("Route chat intent failed, fallback to keyword rule: {}", e.getMessage());
            return fallbackIntent(message);
        }
    }

    private AppChatIntentEnum parseIntent(String rawIntent) {
        if (StrUtil.isBlank(rawIntent)) {
            return null;
        }
        String normalized = StrUtil.trim(rawIntent).toLowerCase();
        if (normalized.contains(AppChatIntentEnum.CUSTOMER_REPLY.getValue())
                || normalized.contains("customer_reply")
                || normalized.contains("customer reply")) {
            return AppChatIntentEnum.CUSTOMER_REPLY;
        }
        if (normalized.contains(AppChatIntentEnum.CODE_GEN.getValue())
                || normalized.contains("code_gen")
                || normalized.contains("code gen")) {
            return AppChatIntentEnum.CODE_GEN;
        }
        return null;
    }

    private AppChatIntentEnum fallbackIntent(String message) {
        String normalized = StrUtil.trim(message).toLowerCase();
        boolean asksQuestion = StrUtil.containsAny(normalized,
                "为什么", "怎么", "怎样", "说说", "解释", "什么意思",
                "刚刚", "为啥", "怎么设计", "做了什么", "是什么",
                "why", "how", "what", "explain");
        boolean asksForCodeChange = StrUtil.containsAny(normalized,
                "修改", "生成", "新增", "增加", "实现", "调整",
                "重构", "改一下", "优化", "修复", "改样式", "改布局",
                "build", "generate", "modify", "update", "change", "fix");
        if (asksQuestion && !asksForCodeChange) {
            return AppChatIntentEnum.CUSTOMER_REPLY;
        }
        return AppChatIntentEnum.CODE_GEN;
    }

    private Flux<AgentStreamEvent> replyToCustomer(App app, String userMessage, User loginUser) {
        return Flux.create(sink -> {
            try {
                String prompt = buildCustomerReplyPrompt(app, userMessage, loginUser.getId());
                String reply = customerSupportService.reply(prompt);
                String finalReply = StrUtil.blankToDefault(reply, "我先回答你这个问题。如果你希望我直接改页面，也可以继续告诉我具体要怎么改。");
                chatHistoryService.addChatMessage(app.getId(), finalReply,
                        ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                sink.next(AgentStreamEvent.assistant(finalReply));
                sink.next(AgentStreamEvent.done());
                sink.complete();
            } catch (Exception e) {
                log.error("Customer reply failed, appId: {}, error: {}", app.getId(), e.getMessage(), e);
                String fallbackReply = buildFallbackCustomerReply(app, userMessage);
                chatHistoryService.addChatMessage(app.getId(), fallbackReply,
                        ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                sink.next(AgentStreamEvent.assistant(fallbackReply));
                sink.next(AgentStreamEvent.done());
                sink.complete();
            }
        });
    }

    private String buildFallbackCustomerReply(App app, String userMessage) {
        StringBuilder reply = new StringBuilder("我先结合当前工程给你一个简要说明。");
        try {
            CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
            if (codeGenTypeEnum != null) {
                AppProjectSnapshotVO snapshotVO =
                        aiCodeGeneratorFacade.buildProjectSnapshot(codeGenTypeEnum, app.getId(), "Current snapshot");
                if (snapshotVO != null && StrUtil.isNotBlank(snapshotVO.getSummary())) {
                    reply.append("\n\n当前我能确认的是：").append(snapshotVO.getSummary());
                }
            }
        } catch (Exception e) {
            log.warn("Build fallback customer reply snapshot failed: {}", e.getMessage());
        }
        reply.append("\n\n如果你想了解某一块为什么这样设计，可以直接告诉我页面、组件或功能点，我会按那一部分继续解释。");
        if (StrUtil.containsAnyIgnoreCase(userMessage, "design", "why", "how", "设计", "为什么", "怎么")) {
            reply.append("\n这次我会按设计思路来解释，不会直接改动代码。");
        }
        return reply.toString();
    }

    private String buildCustomerReplyPrompt(App app, String userMessage, Long userId) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("应用名称: ").append(StrUtil.blankToDefault(app.getAppName(), "未命名应用")).append("\n");
        promptBuilder.append("当前项目类型: ").append(StrUtil.blankToDefault(app.getCodeGenType(), "unknown")).append("\n\n");
        AppProjectSnapshotVO snapshotVO = null;
        try {
            CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
            if (codeGenTypeEnum != null) {
                snapshotVO = aiCodeGeneratorFacade.buildProjectSnapshot(codeGenTypeEnum, app.getId(), "Current snapshot");
            }
        } catch (Exception e) {
            log.warn("Build snapshot for customer reply failed: {}", e.getMessage());
        }
        if (snapshotVO != null) {
            promptBuilder.append("当前项目摘要: ")
                    .append(StrUtil.blankToDefault(snapshotVO.getSummary(), "暂无摘要"))
                    .append("\n");
            List<AppProjectFileVO> files = snapshotVO.getFiles();
            if (CollUtil.isNotEmpty(files)) {
                promptBuilder.append("当前文件列表:\n");
                files.stream()
                        .limit(12)
                        .forEach(file -> promptBuilder.append("- ").append(file.getPath()).append("\n"));
            }
            promptBuilder.append("\n");
        }
        List<ChatHistory> historyList = chatHistoryService.listChatHistory(app.getId(), null, 6);
        if (CollUtil.isNotEmpty(historyList)) {
            promptBuilder.append("最近对话记录:\n");
            historyList.forEach(history -> promptBuilder.append(history.getMessageType())
                    .append(": ")
                    .append(history.getMessage())
                    .append("\n"));
            promptBuilder.append("\n");
        }
        promptBuilder.append("客户当前问题:\n").append(userMessage);
        return promptBuilder.toString();
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "initPrompt cannot be empty");
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(appAddRequest.getCodeGenType());
        if (codeGenTypeEnum == null) {
            codeGenTypeEnum = CodeGenTypeEnum.HTML;
        }
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        app.setCodeGenType(codeGenTypeEnum.getValue());
        if (StrUtil.isBlank(app.getAppName())) {
            app.setAppName(StrUtil.maxLength(initPrompt, 12));
        }
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "Create app failed");
        log.info("App created, id: {}, type: {}", app.getId(), app.getCodeGenType());
        return app.getId();
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "Not logged in");
        App app = validateAndGetOwnedApp(appId, loginUser);
        String deployKey = StrUtil.blankToDefault(app.getDeployKey(), RandomUtil.randomString(6));

        String sourceDirName = app.getCodeGenType() + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        File sourceDir = prepareDeploySourceDir(sourceDirPath, app.getCodeGenType());
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Source project directory does not exist");
        }

        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            File deployDir = new File(deployDirPath);
            if (deployDir.exists()) {
                FileUtil.clean(deployDir);
            }
            FileUtil.copyContent(sourceDir, deployDir, true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Deploy app failed: " + e.getMessage());
        }

        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "Update deploy state failed");

        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    private File prepareDeploySourceDir(String sourceDirPath, String codeGenType) {
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "App source code does not exist");
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue project build failed");
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists() || !distDir.isDirectory(),
                    ErrorCode.SYSTEM_ERROR, "Vue project dist directory not found");
            log.info("Vue project dist ready: {}", distDir.getAbsolutePath());
            return distDir;
        }
        return sourceDir;
    }

    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        log.info("Reserved screenshot hook, appId: {}, appUrl: {}", appId, appUrl);
    }

    @Override
    public AppProjectSnapshotVO getProjectSnapshot(Long appId, User loginUser) {
        App app = validateAndGetOwnedApp(appId, loginUser);
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            aiCodeGeneratorFacade.ensureVuePreview(appId);
        }
        return aiCodeGeneratorFacade.buildProjectSnapshot(codeGenTypeEnum, appId, "Project snapshot loaded");
    }

    @Override
    public byte[] downloadAppCode(Long appId, User loginUser) {
        App app = validateAndGetOwnedApp(appId, loginUser);
        String rootDirName = app.getCodeGenType() + "_" + appId;
        File projectDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, rootDirName);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.NOT_FOUND_ERROR, "Project code not found");
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            List<File> fileList = FileUtil.loopFiles(projectDir);
            for (File file : fileList) {
                String relativePath = FileUtil.subPath(projectDir.getAbsolutePath(), file).replace("\\", "/");
                zipOutputStream.putNextEntry(new ZipEntry(relativePath));
                zipOutputStream.write(FileUtil.readBytes(file));
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Zip download failed: " + e.getMessage());
        }
    }

    @Override
    public boolean deleteApp(Serializable id, User loginUser) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR);
        long appId = Long.parseLong(id.toString());
        App app = validateAndGetOwnedApp(appId, loginUser);
        deleteAppFiles(app);
        return removeById(id);
    }

    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("Delete chat history failed, appId: {}, error: {}", appId, e.getMessage(), e);
        }
        return super.removeById(id);
    }

    private void deleteAppFiles(App app) {
        String sourceDirName = app.getCodeGenType() + "_" + app.getId();
        File projectDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, sourceDirName);
        if (projectDir.exists()) {
            FileUtil.del(projectDir);
            log.info("Deleted project dir: {}", projectDir.getAbsolutePath());
        }
        if (StrUtil.isNotBlank(app.getDeployKey())) {
            File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR, app.getDeployKey());
            if (deployDir.exists()) {
                FileUtil.del(deployDir);
                log.info("Deleted deploy dir: {}", deployDir.getAbsolutePath());
            }
        }
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        Set<Long> userIds = appList.stream().map(App::getUserId).collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = new AppVO();
            BeanUtil.copyProperties(app, appVO);
            appVO.setUser(userVOMap.get(app.getUserId()));
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Query request is null");
        }
        return QueryWrapper.create()
                .eq("id", appQueryRequest.getId())
                .like("appName", appQueryRequest.getAppName())
                .like("cover", appQueryRequest.getCover())
                .like("initPrompt", appQueryRequest.getInitPrompt())
                .eq("codeGenType", appQueryRequest.getCodeGenType())
                .eq("deployKey", appQueryRequest.getDeployKey())
                .eq("priority", appQueryRequest.getPriority())
                .eq("userId", appQueryRequest.getUserId())
                .orderBy(appQueryRequest.getSortField(), "ascend".equals(appQueryRequest.getSortOrder()));
    }

    private App validateAndGetOwnedApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "App not found");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "No permission to access this app");
        }
        return app;
    }
}
