package com.hezitu.heaicodemother.langgraph4j.state;

import com.hezitu.heaicodemother.langgraph4j.model.ImageCollectionPlan;
import com.hezitu.heaicodemother.langgraph4j.model.ImageResource;
import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    @Serial
    private static final long serialVersionUID = 1L;

    private Long appId;

    private Long userId;

    private String currentStep;

    private String originalPrompt;

    private String imageListStr;

    private List<ImageResource> imageList;

    private ImageCollectionPlan imageCollectionPlan;

    private String enhancedPrompt;

    private CodeGenTypeEnum preferredGenerationType;

    private CodeGenTypeEnum generationType;

    private String generatedCodeDir;

    private String buildResultDir;

    private String errorMessage;

    private transient Consumer<AgentStreamEvent> eventPublisher;

    public static WorkflowContext getContext(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }
}
