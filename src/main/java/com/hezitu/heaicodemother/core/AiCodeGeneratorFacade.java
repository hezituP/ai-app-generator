package com.hezitu.heaicodemother.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hezitu.heaicodemother.ai.AiCodeGeneratorService;
import com.hezitu.heaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.hezitu.heaicodemother.ai.model.HtmlCodeResult;
import com.hezitu.heaicodemother.ai.model.MultiFileCodeResult;
import com.hezitu.heaicodemother.constant.AppConstant;
import com.hezitu.heaicodemother.core.parser.CodeParserExecutor;
import com.hezitu.heaicodemother.core.saver.CodeFileSaverExecutor;
import com.hezitu.heaicodemother.exception.BusinessException;
import com.hezitu.heaicodemother.exception.ErrorCode;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
import com.hezitu.heaicodemother.model.vo.AppProjectFileVO;
import com.hezitu.heaicodemother.model.vo.AppProjectSnapshotVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        return generateAndSaveCode(userMessage, codeGenTypeEnum, appId, null);
    }

    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, Long userId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, userId);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(1, userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "当前场景仅支持非流式生成 HTML 或多文件项目");
        };
    }

    public Flux<AgentStreamEvent> generateAndSaveCodeStream(String userMessage,
                                                            CodeGenTypeEnum codeGenTypeEnum,
                                                            Long appId) {
        return generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId, null);
    }

    public Flux<AgentStreamEvent> generateAndSaveCodeStream(String userMessage,
                                                            CodeGenTypeEnum codeGenTypeEnum,
                                                            Long appId,
                                                            Long userId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, userId);
        Flux<String> codeStream = switch (codeGenTypeEnum) {
            case HTML -> aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
            case MULTI_FILE -> aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
            case VUE_PROJECT -> aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
        };
        return processCodeStream(codeStream, codeGenTypeEnum, appId);
    }

    public AppProjectSnapshotVO buildProjectSnapshot(CodeGenTypeEnum codeGenType, Long appId, String summary) {
        String rootDirName = codeGenType.getValue() + "_" + appId;
        File projectDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, rootDirName);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return null;
        }
        List<AppProjectFileVO> files = FileUtil.loopFiles(projectDir)
                .stream()
                .sorted(Comparator.comparing(file -> FileUtil.subPath(projectDir.getAbsolutePath(), file)))
                .map(file -> {
                    AppProjectFileVO fileVO = new AppProjectFileVO();
                    fileVO.setPath(FileUtil.subPath(projectDir.getAbsolutePath(), file).replace("\\", "/"));
                    fileVO.setContent(FileUtil.readUtf8String(file));
                    return fileVO;
                })
                .toList();
        AppProjectSnapshotVO snapshotVO = new AppProjectSnapshotVO();
        snapshotVO.setCodeGenType(codeGenType.getValue());
        snapshotVO.setRootDirName(rootDirName);
        snapshotVO.setSummary(summary);
        snapshotVO.setFiles(files);
        snapshotVO.setEntryFilePath(resolveEntryFilePath(codeGenType, files));
        if (codeGenType != CodeGenTypeEnum.VUE_PROJECT) {
            snapshotVO.setPreviewUrl("/api/static/" + rootDirName + "/");
        }
        return snapshotVO;
    }

    private String resolveEntryFilePath(CodeGenTypeEnum codeGenType, List<AppProjectFileVO> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            return files.stream()
                    .map(AppProjectFileVO::getPath)
                    .filter(path -> StrUtil.equalsAny(path, "src/main.ts", "src/main.js", "src/App.vue", "README.md"))
                    .findFirst()
                    .orElse(files.get(0).getPath());
        }
        return files.stream()
                .map(AppProjectFileVO::getPath)
                .filter(path -> StrUtil.equals(path, "index.html"))
                .findFirst()
                .orElse(files.get(0).getPath());
    }

    private Flux<AgentStreamEvent> processCodeStream(Flux<String> codeStream,
                                                     CodeGenTypeEnum codeGenType,
                                                     Long appId) {
        return Flux.create(sink -> {
            sink.next(AgentStreamEvent.status("Agent 已接管任务，正在分析需求"));
            sink.next(AgentStreamEvent.status("正在生成 " + codeGenType.getText()));
            StringBuilder codeBuilder = new StringBuilder();
            AtomicInteger chunkCounter = new AtomicInteger();
            Disposable disposable = codeStream.subscribe(chunk -> {
                codeBuilder.append(chunk);
                int current = chunkCounter.incrementAndGet();
                if (current == 20) {
                    sink.next(AgentStreamEvent.status("正在编排项目文件与依赖"));
                } else if (current == 60) {
                    sink.next(AgentStreamEvent.status("正在整理可落地的工程结构"));
                }
            }, error -> {
                log.error("代码生成失败, appId: {}, error: {}", appId, error.getMessage(), error);
                sink.next(AgentStreamEvent.error("代码生成失败: " + error.getMessage()));
                sink.next(AgentStreamEvent.done());
                sink.complete();
            }, () -> {
                try {
                    sink.next(AgentStreamEvent.status("代码生成完成，正在解析输出"));
                    String completeCode = codeBuilder.toString();
                    Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                    File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                    AppProjectSnapshotVO snapshot = buildProjectSnapshot(codeGenType, appId, extractSummary(completeCode, codeGenType));
                    log.info("代码保存成功, appId: {}, dir: {}", appId, saveDir.getAbsolutePath());
                    sink.next(AgentStreamEvent.status("项目文件已写入工作目录"));
                    sink.next(AgentStreamEvent.result("工程生成完成", snapshot));
                } catch (Exception e) {
                    log.error("代码解析或保存失败, appId: {}, error: {}", appId, e.getMessage(), e);
                    sink.next(AgentStreamEvent.error("代码解析或保存失败: " + e.getMessage()));
                } finally {
                    sink.next(AgentStreamEvent.done());
                    sink.complete();
                }
            });
            sink.onDispose(disposable);
        });
    }

    private String extractSummary(String completeCode, CodeGenTypeEnum codeGenType) {
        if (codeGenType != CodeGenTypeEnum.VUE_PROJECT) {
            return "工程已生成，可继续增量修改。";
        }
        int markerIndex = completeCode.indexOf("<<FILE_START:");
        if (markerIndex > 0) {
            return completeCode.substring(0, markerIndex).trim();
        }
        return "Vue3 + Vite 工程已生成，可继续让 Agent 增量修改。";
    }
}
