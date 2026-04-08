package com.hezitu.heaicodemother.workflow.langgraph4j.model;

import com.hezitu.heaicodemother.model.enums.CodeGenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    private String currentStep;

    private String originalPrompt;

    private String imageListStr;

    private List<ImageResource> imageList;

    private String enhancedPrompt;

    private CodeGenTypeEnum generationType;

    private String generatedCodeDir;

    private String buildResultDir;

    private String errorMessage;

    @Serial
    private static final long serialVersionUID = 1L;

    public static WorkflowContext getContext(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }
}
