package com.hezitu.heaicodemother.langgraph4j.node;

import com.hezitu.heaicodemother.core.AiCodeGeneratorFacade;
import com.hezitu.heaicodemother.langgraph4j.state.WorkflowContext;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public final class ProjectBuilderNode {

    private ProjectBuilderNode() {
    }

    public static AsyncNodeAction<MessagesState<String>> create(AiCodeGeneratorFacade aiCodeGeneratorFacade) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Consumer<AgentStreamEvent> publisher = context.getEventPublisher();
            context.setCurrentStep("project_build");
            if (aiCodeGeneratorFacade.ensureVuePreview(context.getAppId())) {
                context.setBuildResultDir(new File(context.getGeneratedCodeDir(), "dist").getAbsolutePath());
            } else {
                if (publisher != null) {
                    publisher.accept(AgentStreamEvent.status("preview-build-failed"));
                }
                context.setBuildResultDir(context.getGeneratedCodeDir());
            }
            return WorkflowContext.saveContext(context);
        });
    }
}
