package com.hezitu.heaicodemother.langgraph4j.node;

import com.hezitu.heaicodemother.constant.AppConstant;
import com.hezitu.heaicodemother.core.AiCodeGeneratorFacade;
import com.hezitu.heaicodemother.langgraph4j.state.WorkflowContext;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.time.Duration;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public final class CodeGeneratorNode {

    private CodeGeneratorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create(AiCodeGeneratorFacade aiCodeGeneratorFacade) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Consumer<AgentStreamEvent> publisher = context.getEventPublisher();
            aiCodeGeneratorFacade.generateAndSaveCodeStream(
                            context.getEnhancedPrompt(),
                            context.getGenerationType(),
                            context.getAppId(),
                            context.getUserId()
                    )
                    .doOnNext(event -> {
                        if (publisher == null) {
                            return;
                        }
                        if ("error".equals(event.getType())
                                || "assistant".equals(event.getType())
                                || "assistant_delta".equals(event.getType())) {
                            publisher.accept(event);
                            return;
                        }
                        if ("status".equals(event.getType())) {
                            publisher.accept(AgentStreamEvent.assistant(toAssistantMessage(event.getMessage())));
                        }
                    })
                    .blockLast(Duration.ofMinutes(10));
            File generatedDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR,
                    context.getGenerationType().getValue() + "_" + context.getAppId());
            context.setCurrentStep("code_generation");
            context.setGeneratedCodeDir(generatedDir.getAbsolutePath());
            if (generatedDir.getParentFile() != null
                    && AppConstant.CODE_OUTPUT_ROOT_DIR.equals(generatedDir.getParentFile().getPath())) {
                context.setGeneratedCodeDir(generatedDir.getAbsolutePath());
            }
            return WorkflowContext.saveContext(context);
        });
    }

    private static String toAssistantMessage(String message) {
        if (message == null || message.isBlank()) {
            return "我正在继续处理这次生成任务。";
        }
        if (message.contains("分析需求")) {
            return "我已经接到你的需求，正在梳理这次要改动的重点。";
        }
        if (message.contains("正在生成")) {
            return "我正在组织页面结构和代码内容。";
        }
        if (message.contains("编排项目文件")) {
            return "我在整理项目文件结构，确保代码可以直接落地。";
        }
        if (message.contains("整理可落地的工程结构")) {
            return "我在补齐工程结构和依赖关系。";
        }
        if (message.contains("代码生成完成")) {
            return "代码主体已经生成出来了，我正在做最后的整理。";
        }
        if (message.contains("Vue 工程已生成")) {
            return "Vue 项目已经生成完成，我正在准备预览构建。";
        }
        if (message.contains("静态预览已构建完成")) {
            return "预览已经准备好了，你马上就可以直接查看页面效果。";
        }
        if (message.contains("静态预览构建失败")) {
            return "代码已经生成完成，不过预览构建这一步出了点问题，我先把源码结果给你。";
        }
        if (message.contains("项目文件已写入工作目录")) {
            return "我已经把这轮结果写入工程目录了。";
        }
        return message;
    }
}
