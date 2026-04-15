package com.hezitu.heaicodemother.langgraph4j;

import com.hezitu.heaicodemother.ai.AiCodeGenTypeRoutingService;
import com.hezitu.heaicodemother.core.AiCodeGeneratorFacade;
import com.hezitu.heaicodemother.langgraph4j.ai.ImageCollectionPlanService;
import com.hezitu.heaicodemother.langgraph4j.node.CodeGeneratorNode;
import com.hezitu.heaicodemother.langgraph4j.node.ImageCollectorNode;
import com.hezitu.heaicodemother.langgraph4j.node.ProjectBuilderNode;
import com.hezitu.heaicodemother.langgraph4j.node.PromptEnhancerNode;
import com.hezitu.heaicodemother.langgraph4j.node.RouterNode;
import com.hezitu.heaicodemother.langgraph4j.state.WorkflowContext;
import com.hezitu.heaicodemother.langgraph4j.tools.ImageSearchTool;
import com.hezitu.heaicodemother.langgraph4j.tools.LogoGeneratorTool;
import com.hezitu.heaicodemother.langgraph4j.tools.UndrawIllustrationTool;
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

    private final ImageCollectionPlanService imageCollectionPlanService;

    private final ImageSearchTool imageSearchTool;

    private final UndrawIllustrationTool undrawIllustrationTool;

    private final LogoGeneratorTool logoGeneratorTool;

    private final AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;

    private final AiCodeGeneratorFacade aiCodeGeneratorFacade;

    public Flux<AgentStreamEvent> executeWorkflowStream(Long appId,
                                                        Long userId,
                                                        String prompt,
                                                        CodeGenTypeEnum preferredGenerationType) {
        return Flux.create(sink -> Thread.startVirtualThread(() -> {
            try {
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
                        String statusMessage = describeStep(currentContext.getCurrentStep());
                        if (statusMessage != null) {
                            sink.next(AgentStreamEvent.status(statusMessage));
                        }
                    }
                }
                AppProjectSnapshotVO snapshot = aiCodeGeneratorFacade.buildProjectSnapshot(
                        lastContext.getGenerationType(), appId, "Workflow completed");
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
                .addNode("image_collector",
                        ImageCollectorNode.create(
                                imageCollectionPlanService,
                                imageSearchTool,
                                undrawIllustrationTool,
                                logoGeneratorTool
                        ))
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
            return null;
        }
        return switch (step) {
            case "image_collection" -> "workflow:image_collection";
            case "prompt_enhancement" -> "workflow:prompt_enhancement";
            case "routing" -> "workflow:routing";
            case "code_generation" -> "workflow:code_generation";
            case "project_build" -> "workflow:project_build";
            default -> null;
        };
    }
}
