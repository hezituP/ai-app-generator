package com.hezitu.heaicodemother.langgraph4j.node;

import com.hezitu.heaicodemother.langgraph4j.ai.ImageCollectionService;
import com.hezitu.heaicodemother.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public final class ImageCollectorNode {

    private ImageCollectorNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create(ImageCollectionService imageCollectionService) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            context.setCurrentStep("image_collection");
            try {
                String imageListStr = imageCollectionService.collectImages(context.getOriginalPrompt());
                context.setImageListStr(imageListStr);
            } catch (Exception e) {
                log.warn("Image collection failed: {}", e.getMessage());
                context.setImageListStr("");
            }
            return WorkflowContext.saveContext(context);
        });
    }
}
