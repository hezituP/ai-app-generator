package com.hezitu.heaicodemother.workflow.langgraph4j;

import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j 基础工作流模板。
 * 当前先提供线性流程骨架，后续可以按实际业务替换节点逻辑和边关系。
 */
@Component
@Slf4j
public class BasicWorkflowTemplate {

    public static final String ANALYZE_REQUIREMENT_NODE = "analyzeRequirement";

    public static final String PREPARE_CONTEXT_NODE = "prepareContext";

    public static final String EXECUTE_TASK_NODE = "executeTask";

    public static final String SUMMARIZE_RESULT_NODE = "summarizeResult";

    public StateGraph<BasicWorkflowState> buildWorkflow() throws GraphStateException {
        return new StateGraph<>(BasicWorkflowState.SCHEMA, BasicWorkflowState::new)
                .addNode(ANALYZE_REQUIREMENT_NODE, createLogNode("进入需求分析节点"))
                .addNode(PREPARE_CONTEXT_NODE, createLogNode("进入上下文准备节点"))
                .addNode(EXECUTE_TASK_NODE, createLogNode("进入任务执行节点"))
                .addNode(SUMMARIZE_RESULT_NODE, createLogNode("进入结果汇总节点"))
                .addEdge(START, ANALYZE_REQUIREMENT_NODE)
                .addEdge(ANALYZE_REQUIREMENT_NODE, PREPARE_CONTEXT_NODE)
                .addEdge(PREPARE_CONTEXT_NODE, EXECUTE_TASK_NODE)
                .addEdge(EXECUTE_TASK_NODE, SUMMARIZE_RESULT_NODE)
                .addEdge(SUMMARIZE_RESULT_NODE, END);
    }

    public void runDemo() throws GraphStateException {
        var compiledGraph = buildWorkflow().compile();
        for (var state : compiledGraph.stream(Map.of(BasicWorkflowState.MESSAGES, "开始执行 LangGraph4j 工作流"))) {
            log.info("工作流状态: {}", state);
        }
    }

    private AsyncNodeAction<BasicWorkflowState> createLogNode(String message) {
        return node_async(state -> {
            log.info(message);
            return Map.of(BasicWorkflowState.MESSAGES, message);
        });
    }
}
