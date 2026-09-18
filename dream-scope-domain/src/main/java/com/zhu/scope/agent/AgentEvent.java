package com.zhu.scope.agent;

/**
 * 对外流式事件。无框架类型；adapter 把 AgentScope 事件编成这些形状。
 */
public sealed interface AgentEvent
        permits AgentEvent.TextDelta, AgentEvent.ToolCall, AgentEvent.ToolResult, AgentEvent.Done, AgentEvent.Error {

    record TextDelta(String text) implements AgentEvent {}

    record ToolCall(String toolName, String input) implements AgentEvent {}

    record ToolResult(String toolName, String output) implements AgentEvent {}

    record Done(String finalOutput, int inputTokens, int outputTokens) implements AgentEvent {
        public Done(String finalOutput) {
            this(finalOutput, 0, 0);
        }

        public Done {
            finalOutput = finalOutput == null ? "" : finalOutput;
            if (inputTokens < 0) {
                inputTokens = 0;
            }
            if (outputTokens < 0) {
                outputTokens = 0;
            }
        }
    }

    record Error(String message) implements AgentEvent {}
}
