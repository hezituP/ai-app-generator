package com.hezitu.heaicodemother.langgraph4j.node;

import com.hezitu.heaicodemother.ai.AiCodeGenTypeRoutingService;
import com.hezitu.heaicodemother.langgraph4j.state.WorkflowContext;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public final class RouterNode {

    private RouterNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create(AiCodeGenTypeRoutingService routingService) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            CodeGenTypeEnum generationType = context.getPreferredGenerationType();
            if (generationType == null) {
                try {
                    generationType = routingService.routeCodeGenType(context.getOriginalPrompt());
                } catch (Exception e) {
                    log.warn("AI routing failed, fallback to HTML: {}", e.getMessage());
                    generationType = CodeGenTypeEnum.HTML;
                }
            }
            context.setCurrentStep("routing");
            context.setGenerationType(generationType);
            return WorkflowContext.saveContext(context);
        });
    }
}
