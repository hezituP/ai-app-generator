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
import com.hezitu.heaicodemother.mapper.AppMapper;
import com.hezitu.heaicodemother.model.dto.app.AppAddRequest;
import com.hezitu.heaicodemother.model.dto.app.AppQueryRequest;
import com.hezitu.heaicodemother.model.entity.App;
import com.hezitu.heaicodemother.model.entity.User;
import com.hezitu.heaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
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

    @Override
    public Flux<AgentStreamEvent> chatToGenCode(Long appId, String message, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        App app = validateAndGetOwnedApp(appId, loginUser);
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }
        chatHistoryService.addChatMessage(appId, message,
                ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        StringBuilder aiResponseBuilder = new StringBuilder();
        return aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId, loginUser.getId())
                .doOnNext(event -> {
                    if (StrUtil.isNotBlank(event.getMessage())) {
                        aiResponseBuilder.append("[").append(event.getType()).append("] ")
                                .append(event.getMessage()).append("\n");
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
                    log.error("AI 生成代码失败, appId: {}, error: {}", appId, e.getMessage(), e);
                    chatHistoryService.addChatMessage(appId,
                            "AI 生成失败: " + e.getMessage(),
                            ChatHistoryMessageTypeEnum.ERROR.getValue(), loginUser.getId());
                });
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化提示词不能为空");
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
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功, id: {}, codeGenType: {}", app.getId(), app.getCodeGenType());
        return app.getId();
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = validateAndGetOwnedApp(appId, loginUser);
        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        String sourceDirName = app.getCodeGenType() + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        File sourceDir = prepareDeploySourceDir(sourceDirPath, app.getCodeGenType());
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
        }
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            File deployDir = new File(deployDirPath);
            if (deployDir.exists()) {
                FileUtil.clean(deployDir);
            }
            FileUtil.copyContent(sourceDir, deployDir, true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败: " + e.getMessage());
        }
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    private File prepareDeploySourceDir(String sourceDirPath, String codeGenType) {
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists() || !distDir.isDirectory(),
                    ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
            return distDir;
        }
        return sourceDir;
    }

    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        log.info("应用截图预留接口, appId: {}, appUrl: {}", appId, appUrl);
    }

    @Override
    public AppProjectSnapshotVO getProjectSnapshot(Long appId, User loginUser) {
        App app = validateAndGetOwnedApp(appId, loginUser);
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        return aiCodeGeneratorFacade.buildProjectSnapshot(codeGenTypeEnum, appId, "已读取当前工程快照");
    }

    @Override
    public byte[] downloadAppCode(Long appId, User loginUser) {
        App app = validateAndGetOwnedApp(appId, loginUser);
        String rootDirName = app.getCodeGenType() + "_" + appId;
        File projectDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, rootDirName);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成");
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
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码打包下载失败: " + e.getMessage());
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
            log.error("删除应用聊天记录失败, appId: {}, error: {}", appId, e.getMessage(), e);
        }
        return super.removeById(id);
    }

    private void deleteAppFiles(App app) {
        String sourceDirName = app.getCodeGenType() + "_" + app.getId();
        File projectDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, sourceDirName);
        if (projectDir.exists()) {
            FileUtil.del(projectDir);
            log.info("已删除应用代码目录: {}", projectDir.getAbsolutePath());
        }
        if (StrUtil.isNotBlank(app.getDeployKey())) {
            File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR, app.getDeployKey());
            if (deployDir.exists()) {
                FileUtil.del(deployDir);
                log.info("已删除应用部署目录: {}", deployDir.getAbsolutePath());
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
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
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
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
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
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该应用");
        }
        return app;
    }
}
