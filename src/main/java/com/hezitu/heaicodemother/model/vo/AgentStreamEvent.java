package com.hezitu.heaicodemother.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentStreamEvent implements Serializable {

    private String type;

    private String message;

    private Object data;

    public static AgentStreamEvent status(String message) {
        return new AgentStreamEvent("status", message, null);
    }

    public static AgentStreamEvent result(String message, Object data) {
        return new AgentStreamEvent("result", message, data);
    }

    public static AgentStreamEvent error(String message) {
        return new AgentStreamEvent("error", message, null);
    }

    public static AgentStreamEvent done() {
        return new AgentStreamEvent("done", "completed", null);
    }
}
