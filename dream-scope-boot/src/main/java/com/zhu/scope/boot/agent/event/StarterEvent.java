package com.zhu.scope.boot.agent.event;

import com.zhu.scope.boot.agent.bean.response.StarterResult;

import java.util.Map;

/** boot 流式事件。SSE 的 event 名与这些形状对应。 */
public sealed interface StarterEvent
        permits StarterEvent.TextDelta,
                StarterEvent.ToolCall,
                StarterEvent.ToolResult,
                StarterEvent.Hint,
                StarterEvent.Done,
                StarterEvent.Error {

    record TextDelta(String text) implements StarterEvent {}

    record ToolCall(String toolName, String input) implements StarterEvent {}

    record ToolResult(String toolName, String output) implements StarterEvent {}

    record Hint(String text) implements StarterEvent {
        public Hint {
            text = text == null ? "" : text;
        }
    }

    record Done(String finalOutput, int inputTokens, int outputTokens, Map<String, Object> data, boolean planActive)
            implements StarterEvent {

        public Done(String finalOutput) {
            this(finalOutput, 0, 0, null, false);
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
            data = StarterResult.copyData(data);
        }
    }

    record Error(String message) implements StarterEvent {}
}
