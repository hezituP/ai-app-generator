package com.hezitu.heaicodemother.workflow.langgraph4j.node;

import com.hezitu.heaicodemother.workflow.langgraph4j.model.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");

            String generatedCodeDir = "/tmp/generated/fake-code";

            if (context != null) {
                context.setCurrentStep("代码生成");
                context.setGeneratedCodeDir(generatedCodeDir);
            }
            log.info("代码生成完成，目录: {}", generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
