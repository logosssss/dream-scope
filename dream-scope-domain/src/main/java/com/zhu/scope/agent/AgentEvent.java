package com.zhu.scope.agent;

import java.util.Map;

/**
 * 对外流式事件。无框架类型；adapter 把 AgentScope 事件编成这些形状。
 */
public sealed interface AgentEvent
        permits AgentEvent.TextDelta,
                AgentEvent.ToolCall,
                AgentEvent.ToolResult,
                AgentEvent.Hint,
                AgentEvent.Done,
                AgentEvent.Error {

    record TextDelta(String text) implements AgentEvent {}

    record ToolCall(String toolName, String input) implements AgentEvent {}

    record ToolResult(String toolName, String output) implements AgentEvent {}

    record Hint(String text) implements AgentEvent {
        public Hint {
            text = text == null ? "" : text;
        }
    }

    record Done(String finalOutput, int inputTokens, int outputTokens, Map<String, Object> data, boolean planActive)
            implements AgentEvent {
        public Done(String finalOutput) {
            this(finalOutput, 0, 0, null, false);
        }

        public Done(String finalOutput, int inputTokens, int outputTokens) {
            this(finalOutput, inputTokens, outputTokens, null, false);
        }

        public Done(String finalOutput, int inputTokens, int outputTokens, Map<String, Object> data) {
            this(finalOutput, inputTokens, outputTokens, data, false);
        }

        public Done {
            finalOutput = finalOutput == null ? "" : finalOutput;
            if (inputTokens < 0) {
                inputTokens = 0;
            }
            if (outputTokens < 0) {
                outputTokens = 0;
            }
            data = AgentInvokeResult.copyData(data);
        }
    }

    record Error(String message) implements AgentEvent {}
}
