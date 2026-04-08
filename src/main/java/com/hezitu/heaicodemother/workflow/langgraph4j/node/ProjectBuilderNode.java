package com.hezitu.heaicodemother.workflow.langgraph4j.node;

import com.hezitu.heaicodemother.workflow.langgraph4j.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");

            String buildResultDir = "/tmp/build/fake-build";

            if (context != null) {
                context.setCurrentStep("项目构建");
                context.setBuildResultDir(buildResultDir);
            }
            log.info("项目构建完成，结果目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
