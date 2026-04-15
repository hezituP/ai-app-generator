package com.hezitu.heaicodemother.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import com.hezitu.heaicodemother.langgraph4j.model.ImageResource;
import com.hezitu.heaicodemother.langgraph4j.state.WorkflowContext;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public final class PromptEnhancerNode {

    private PromptEnhancerNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append(context.getOriginalPrompt());
            List<ImageResource> imageList = context.getImageList();
            if (CollUtil.isNotEmpty(imageList)) {
                promptBuilder.append("\n\nAvailable visual assets:\n");
                for (ImageResource image : imageList) {
                    promptBuilder.append("- ")
                            .append("[")
                            .append(image.getCategory().getText())
                            .append("] ")
                            .append(image.getDescription())
                            .append(": ")
                            .append(image.getUrl())
                            .append("\n");
                }
            } else if (context.getImageListStr() != null && !context.getImageListStr().isBlank()) {
                promptBuilder.append("\n\nAvailable visual assets:\n");
                promptBuilder.append(context.getImageListStr());
            }
            context.setCurrentStep("prompt_enhancement");
            context.setEnhancedPrompt(promptBuilder.toString());
            return WorkflowContext.saveContext(context);
        });
    }
}
