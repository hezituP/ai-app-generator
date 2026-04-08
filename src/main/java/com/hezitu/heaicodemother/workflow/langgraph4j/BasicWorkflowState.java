package com.hezitu.heaicodemother.workflow.langgraph4j;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BasicWorkflowState extends AgentState {

    public static final String MESSAGES = "messages";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES, Channels.appender(ArrayList::new)
    );

    public BasicWorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<String>> messages() {
        return (Optional<List<String>>) (Optional<?>) value(MESSAGES);
    }
}
