package com.hezitu.heaicodemother.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class AiModelMonitorListener implements ChatModelListener {

    private static final String REQUEST_START_TIME_KEY = "request_start_time";
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";

    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
        MonitorContext monitorContext = defaultContext(MonitorContextHolder.getContext());
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, monitorContext);
        aiModelMetricsCollector.recordRequest(
                monitorContext.getUserId(),
                monitorContext.getAppId(),
                requestContext.chatRequest().modelName(),
                "started"
        );
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Map<Object, Object> attributes = responseContext.attributes();
        MonitorContext context = defaultContext((MonitorContext) attributes.get(MONITOR_CONTEXT_KEY));
        String modelName = responseContext.chatResponse().modelName();
        aiModelMetricsCollector.recordRequest(context.getUserId(), context.getAppId(), modelName, "success");
        recordResponseTime(attributes, context.getUserId(), context.getAppId(), modelName);
        recordTokenUsage(responseContext, context.getUserId(), context.getAppId(), modelName);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        MonitorContext context = defaultContext(MonitorContextHolder.getContext());
        String modelName = errorContext.chatRequest().modelName();
        aiModelMetricsCollector.recordRequest(context.getUserId(), context.getAppId(), modelName, "error");
        aiModelMetricsCollector.recordError(
                context.getUserId(),
                context.getAppId(),
                modelName,
                errorContext.error().getClass().getSimpleName()
        );
        recordResponseTime(errorContext.attributes(), context.getUserId(), context.getAppId(), modelName);
    }

    private void recordResponseTime(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        Instant startTime = (Instant) attributes.get(REQUEST_START_TIME_KEY);
        if (startTime == null) {
            return;
        }
        aiModelMetricsCollector.recordResponseTime(userId, appId, modelName, Duration.between(startTime, Instant.now()));
    }

    private void recordTokenUsage(ChatModelResponseContext responseContext, String userId, String appId, String modelName) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        if (tokenUsage == null) {
            return;
        }
        aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "input", tokenUsage.inputTokenCount());
        aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "output", tokenUsage.outputTokenCount());
        aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "total", tokenUsage.totalTokenCount());
    }

    private MonitorContext defaultContext(MonitorContext context) {
        if (context != null) {
            return context;
        }
        return MonitorContext.builder()
                .userId("unknown")
                .appId("unknown")
                .build();
    }
}
