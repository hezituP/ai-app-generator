package com.hezitu.heaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
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
import com.hezitu.heaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
import com.hezitu.heaicodemother.model.vo.AppProjectFileVO;
import com.hezitu.heaicodemother.model.vo.AppProjectSnapshotVO;
import com.hezitu.heaicodemother.model.vo.AppVO;
import com.hezitu.heaicodemother.model.vo.UserVO;
import com.hezitu.heaicodemother.monitor.MonitorContext;
import com.hezitu.heaicodemother.monitor.MonitorContextHolder;
import com.hezitu.heaicodemother.service.AppService;
import com.hezitu.heaicodemother.service.ChatHistoryService;
import com.hezitu.heaicodemother.service.ScreenshotService;
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
    private ScreenshotService screenshotService;

    @Resource
    private CodeGenWorkflow codeGenWorkflow;

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
        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId(String.valueOf(loginUser.getId()))
                .appId(String.valueOf(appId))
                .build());
        return codeGenWorkflow.executeWorkflowStream(appId, loginUser.getId(), message, codeGenTypeEnum)
                .doOnError(e -> {
                    log.error("AI code generation failed, appId: {}, error: {}", appId, e.getMessage(), e);
                    chatHistoryService.addChatMessage(appId,
                            "AI generation failed: " + e.getMessage(),
                            ChatHistoryMessageTypeEnum.ERROR.getValue(), loginUser.getId());
                })
                .doFinally(signalType -> MonitorContextHolder.clearContext());
    }

    

    private void appendKeyFiles(StringBuilder promptBuilder, List<AppProjectFileVO> files) {
        if (CollUtil.isEmpty(files)) {
            return;
        }
        List<String> keyPaths = files.stream()
                .map(AppProjectFileVO::getPath)
                .filter(path -> StrUtil.equalsAny(path,
                                "src/App.vue",
                                "src/main.ts",
                                "src/main.js",
                                "src/router/index.ts",
                                "src/router/index.js",
                                "index.html")
                        || path.startsWith("src/views/")
                        || path.startsWith("src/components/"))
                .limit(8)
                .toList();
        if (CollUtil.isNotEmpty(keyPaths)) {
            promptBuilder.append("关键文件:\n");
            keyPaths.forEach(path -> promptBuilder.append("- ").append(path).append("\n"));
        }
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
        Thread.startVirtualThread(() -> {
            try {
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
                if (StrUtil.isBlank(screenshotUrl)) {
                    return;
                }
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updated = this.updateById(updateApp);
                ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "Update app cover failed");
            } catch (Exception e) {
                log.error("Generate app screenshot failed, appId: {}, appUrl: {}", appId, appUrl, e);
            }
        });
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
