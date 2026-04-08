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
                                || "assistant_delta".equals(event.getType())
                                || "status".equals(event.getType())) {
                            publisher.accept(event);
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
}
