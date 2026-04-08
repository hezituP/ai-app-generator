package com.hezitu.heaicodemother.langgraph4j;

import com.hezitu.heaicodemother.ai.AiCodeGenTypeRoutingService;
import com.hezitu.heaicodemother.core.AiCodeGeneratorFacade;
import com.hezitu.heaicodemother.langgraph4j.ai.ImageCollectionService;
import com.hezitu.heaicodemother.langgraph4j.node.CodeGeneratorNode;
import com.hezitu.heaicodemother.langgraph4j.node.ImageCollectorNode;
import com.hezitu.heaicodemother.langgraph4j.node.ProjectBuilderNode;
import com.hezitu.heaicodemother.langgraph4j.node.PromptEnhancerNode;
import com.hezitu.heaicodemother.langgraph4j.node.RouterNode;
import com.hezitu.heaicodemother.langgraph4j.state.WorkflowContext;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
import com.hezitu.heaicodemother.model.vo.AppProjectSnapshotVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Service
@Slf4j
@RequiredArgsConstructor
public class CodeGenWorkflow {

    private final ImageCollectionService imageCollectionService;

    private final AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    private final AiCodeGeneratorFacade aiCodeGeneratorFacade;

    public Flux<AgentStreamEvent> executeWorkflowStream(Long appId,
                                                        Long userId,
                                                        String prompt,
                                                        CodeGenTypeEnum preferredGenerationType) {
        return Flux.create(sink -> Thread.startVirtualThread(() -> {
            try {
                sink.next(AgentStreamEvent.assistant("我先看看你的需求，并结合当前工程准备一个合适的实现方案。"));
                CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                WorkflowContext initialContext = WorkflowContext.builder()
                        .appId(appId)
                        .userId(userId)
                        .originalPrompt(prompt)
                        .preferredGenerationType(preferredGenerationType)
                        .currentStep("init")
                        .eventPublisher(sink::next)
                        .build();
                WorkflowContext lastContext = initialContext;
                for (NodeOutput<MessagesState<String>> step : workflow.stream(
                        Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                    WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                    if (currentContext != null) {
                        lastContext = currentContext;
                        String assistantMessage = describeStep(currentContext.getCurrentStep());
                        if (assistantMessage != null) {
                            sink.next(AgentStreamEvent.assistant(assistantMessage));
                        }
                    }
                }
                AppProjectSnapshotVO snapshot = aiCodeGeneratorFacade.buildProjectSnapshot(
                        lastContext.getGenerationType(), appId, "Workflow completed");
                sink.next(AgentStreamEvent.assistant("这轮修改已经完成，我把最新工程和预览结果同步给你。"));
                sink.next(AgentStreamEvent.result("工程结果已准备完成，你可以查看右侧文件和预览。", snapshot));
                sink.next(AgentStreamEvent.done());
                sink.complete();
            } catch (Exception e) {
                log.error("Workflow execution failed", e);
                sink.next(AgentStreamEvent.error("workflow_failed: " + e.getMessage()));
                sink.next(AgentStreamEvent.done());
                sink.complete();
            }
        }));
    }

    private CompiledGraph<MessagesState<String>> createWorkflow() throws GraphStateException {
        return new MessagesStateGraph<String>()
                .addNode("image_collector", ImageCollectorNode.create(imageCollectionService))
                .addNode("prompt_enhancer", PromptEnhancerNode.create())
                .addNode("router", RouterNode.create(aiCodeGenTypeRoutingService))
                .addNode("code_generator", CodeGeneratorNode.create(aiCodeGeneratorFacade))
                .addNode("project_builder", ProjectBuilderNode.create(aiCodeGeneratorFacade))
                .addEdge(START, "image_collector")
                .addEdge("image_collector", "prompt_enhancer")
                .addEdge("prompt_enhancer", "router")
                .addEdge("router", "code_generator")
                .addConditionalEdges("code_generator",
                        edge_async(this::routeAfterCodeGeneration),
                        Map.of(
                                "build", "project_builder",
                                "finish", END
                        ))
                .addEdge("project_builder", END)
                .compile();
    }

    private String routeAfterCodeGeneration(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context.getGenerationType() == CodeGenTypeEnum.VUE_PROJECT) {
            return "build";
        }
        return "finish";
    }

    private String describeStep(String step) {
        if (step == null || step.isBlank()) {
            return "我正在继续处理这次修改。";
        }
        return switch (step) {
            case "image_collection" -> "我先补充一些页面所需的素材和上下文，让后续生成更稳定。";
            case "prompt_enhancement" -> "我在整理需求细节，准备把它转换成更适合生成的描述。";
            case "routing" -> "我在判断这次任务更适合用哪一种项目结构来实现。";
            case "code_generation" -> "我已经开始生成代码了，马上把结果整理出来。";
            case "project_build" -> "我在构建预览环境，这样你可以直接看到页面效果。";
            default -> null;
        };
    }
}
