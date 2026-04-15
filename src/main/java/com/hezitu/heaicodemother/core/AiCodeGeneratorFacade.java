package com.hezitu.heaicodemother.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hezitu.heaicodemother.ai.AiCodeGeneratorService;
import com.hezitu.heaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.hezitu.heaicodemother.ai.model.HtmlCodeResult;
import com.hezitu.heaicodemother.ai.model.MultiFileCodeResult;
import com.hezitu.heaicodemother.constant.AppConstant;
import com.hezitu.heaicodemother.core.builder.VueProjectBuilder;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

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
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "当前场景仅支持非流式生成 HTML 或多文件项目");
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
        String rootDirName = buildRootDirName(codeGenType, appId);
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
        snapshotVO.setSummary(resolveSnapshotSummary(codeGenType, summary, files));
        snapshotVO.setFiles(files);
        snapshotVO.setEntryFilePath(resolveEntryFilePath(codeGenType, files));
        snapshotVO.setPreviewUrl(resolvePreviewUrl(codeGenType, projectDir, rootDirName));
        return snapshotVO;
    }

    public boolean ensureVuePreview(Long appId) {
        String rootDirName = buildRootDirName(CodeGenTypeEnum.VUE_PROJECT, appId);
        File projectDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, rootDirName);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return false;
        }
        File distIndexFile = new File(projectDir, "dist/index.html");
        if (distIndexFile.exists() && !needsVuePreviewRebuild(distIndexFile)) {
            return true;
        }
        return vueProjectBuilder.buildProject(projectDir.getAbsolutePath());
    }

    private String buildRootDirName(CodeGenTypeEnum codeGenType, Long appId) {
        return codeGenType.getValue() + "_" + appId;
    }

    private String resolvePreviewUrl(CodeGenTypeEnum codeGenType, File projectDir, String rootDirName) {
        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            File distIndexFile = new File(projectDir, "dist/index.html");
            if (distIndexFile.exists()) {
                return "/api/static/" + rootDirName + "/dist/";
            }
            return null;
        }
        return "/api/static/" + rootDirName + "/";
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

    private boolean needsVuePreviewRebuild(File distIndexFile) {
        try {
            String indexHtml = FileUtil.readUtf8String(distIndexFile);
            return StrUtil.containsAny(indexHtml,
                    "src=\"/assets/",
                    "href=\"/assets/",
                    "src='/assets/",
                    "href='/assets/");
        } catch (Exception e) {
            log.warn("Check vue preview build output failed: {}", e.getMessage());
            return true;
        }
    }

    private String resolveSnapshotSummary(CodeGenTypeEnum codeGenType, String summary, List<AppProjectFileVO> files) {
        if (StrUtil.isNotBlank(summary) && !"Current snapshot".equalsIgnoreCase(summary.trim())) {
            return summary.trim();
        }
        return generateSummaryFromFiles(codeGenType, files);
    }

    private String generateSummaryFromFiles(CodeGenTypeEnum codeGenType, List<AppProjectFileVO> files) {
        if (files == null || files.isEmpty()) {
            return "当前项目文件已生成，但暂时还没有可用的结构摘要。";
        }
        return switch (codeGenType) {
            case HTML -> buildHtmlSummary(files);
            case MULTI_FILE -> buildMultiFileSummary(files);
            case VUE_PROJECT -> buildVueSummary(files);
        };
    }

    private String buildHtmlSummary(List<AppProjectFileVO> files) {
        List<String> keyFiles = files.stream()
                .map(AppProjectFileVO::getPath)
                .filter(path -> StrUtil.equalsAny(path, "index.html", "style.css", "script.js"))
                .toList();
        if (!keyFiles.isEmpty()) {
            return "当前是一个单页前端项目，主要文件包括 " + String.join("、", keyFiles) + "，可以直接围绕页面结构、样式和交互继续修改。";
        }
        return "当前是一个单页前端项目，页面代码已经生成完成，可以继续围绕结构、样式和交互做增量调整。";
    }

    private String buildMultiFileSummary(List<AppProjectFileVO> files) {
        List<String> topFiles = files.stream()
                .map(AppProjectFileVO::getPath)
                .filter(path -> !path.contains("/"))
                .limit(6)
                .toList();
        if (!topFiles.isEmpty()) {
            return "当前是一个多文件前端项目，根目录下的关键文件有 " + String.join("、", topFiles) + "，适合继续按模块或页面逐步修改。";
        }
        return "当前是一个多文件前端项目，代码已经按文件拆分保存，可以继续针对具体页面或模块做调整。";
    }

    private String buildVueSummary(List<AppProjectFileVO> files) {
        List<String> filePaths = files.stream().map(AppProjectFileVO::getPath).toList();
        long viewCount = filePaths.stream()
                .filter(path -> path.startsWith("src/views/") && path.endsWith(".vue"))
                .count();
        long componentCount = filePaths.stream()
                .filter(path -> path.startsWith("src/components/") && path.endsWith(".vue"))
                .count();
        boolean hasRouter = filePaths.stream().anyMatch(path -> StrUtil.equalsAny(path, "src/router/index.ts", "src/router/index.js"));
        List<String> routeFiles = filePaths.stream()
                .filter(path -> path.startsWith("src/views/") && path.endsWith(".vue"))
                .map(path -> StrUtil.removePrefix(path, "src/views/"))
                .limit(4)
                .toList();
        List<String> highlights = new ArrayList<>();
        highlights.add("这是一个 Vue3 + Vite 项目");
        if (hasRouter) {
            highlights.add("已经接入路由");
        }
        if (viewCount > 0) {
            highlights.add("包含 " + viewCount + " 个页面视图");
        }
        if (componentCount > 0) {
            highlights.add("包含 " + componentCount + " 个可复用组件");
        }
        StringBuilder summary = new StringBuilder(String.join("，", highlights)).append("。");
        if (!routeFiles.isEmpty()) {
            summary.append(" 当前可见的页面文件有 ").append(String.join("、", routeFiles)).append("。");
        }
        String appVueOutline = extractVueOutline(files, "src/App.vue");
        if (StrUtil.isNotBlank(appVueOutline)) {
            summary.append(" 应用外层结构大致是：").append(appVueOutline).append("。");
        } else {
            summary.append(" 你可以继续围绕页面布局、组件样式或交互流程做增量修改。");
        }
        return summary.toString();
    }

    private String extractVueOutline(List<AppProjectFileVO> files, String path) {
        AppProjectFileVO appFile = files.stream()
                .filter(file -> StrUtil.equals(file.getPath(), path))
                .findFirst()
                .orElse(null);
        if (appFile == null || StrUtil.isBlank(appFile.getContent())) {
            return null;
        }
        String template = extractBetween(appFile.getContent(), "<template>", "</template>");
        if (StrUtil.isBlank(template)) {
            return null;
        }
        String normalized = template
                .replaceAll("(?s)<!--.*?-->", " ")
                .replaceAll("(?s)<script.*?</script>", " ")
                .replaceAll("(?s)<style.*?</style>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120) + "...";
        }
        normalized = normalized.replace("<", " <").trim();
        return normalized;
    }

    private String extractBetween(String text, String start, String end) {
        int startIndex = text.indexOf(start);
        if (startIndex < 0) {
            return null;
        }
        startIndex += start.length();
        int endIndex = text.indexOf(end, startIndex);
        if (endIndex < 0) {
            return null;
        }
        return text.substring(startIndex, endIndex).trim();
    }

    private Flux<AgentStreamEvent> processCodeStream(Flux<String> codeStream,
                                                     CodeGenTypeEnum codeGenType,
                                                     Long appId) {
        return Flux.create(sink -> {
            sink.next(AgentStreamEvent.status("Agent 已接管任务，正在分析你的需求"));
            sink.next(AgentStreamEvent.status("正在生成 " + codeGenType.getText() + " 的工程内容"));
            StringBuilder codeBuilder = new StringBuilder();
            AtomicInteger chunkCounter = new AtomicInteger();
            Disposable disposable = codeStream.subscribe(chunk -> {
                codeBuilder.append(chunk);
                sink.next(AgentStreamEvent.assistantDelta(chunk));
                int current = chunkCounter.incrementAndGet();
                if (current == 20) {
                    sink.next(AgentStreamEvent.status("正在编排项目文件、组件与依赖关系"));
                } else if (current == 60) {
                    sink.next(AgentStreamEvent.status("正在整理可落地的工程结构与输出结果"));
                }
            }, error -> {
                log.error("代码生成失败, appId: {}, error: {}", appId, error.getMessage(), error);
                sink.next(AgentStreamEvent.error("代码生成失败: " + error.getMessage()));
                sink.next(AgentStreamEvent.done());
                sink.complete();
            }, () -> {
                try {
                    sink.next(AgentStreamEvent.status("代码生成完成，正在解析并写入工程文件"));
                    String completeCode = codeBuilder.toString();
                    Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                    File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                    if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
                        sink.next(AgentStreamEvent.status("Vue 工程已生成，正在构建静态预览"));
                        boolean previewBuilt = vueProjectBuilder.buildProject(saveDir.getAbsolutePath());
                        if (previewBuilt) {
                            sink.next(AgentStreamEvent.status("preview-build-success"));
                        } else {
                            sink.next(AgentStreamEvent.status("preview-build-failed"));
                        }
                    }
                    AppProjectSnapshotVO snapshot = buildProjectSnapshot(codeGenType, appId, extractSummary(completeCode, codeGenType));
                    log.info("代码保存成功, appId: {}, dir: {}", appId, saveDir.getAbsolutePath());
                    sink.next(AgentStreamEvent.status("工程快照已准备完成，正在返回文件树与预览信息"));
                    sink.next(AgentStreamEvent.result("本轮工程结果已生成完成", snapshot));
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
