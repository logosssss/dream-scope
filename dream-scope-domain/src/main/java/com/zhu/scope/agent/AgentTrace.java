package com.zhu.scope.agent;

/** 从流事件里抽出同步响应要保留的链路步。正文增量和 Done 不计入。 */
public final class AgentTrace {

    private AgentTrace() {}

    public static AgentTraceStep from(AgentEvent event) {
        if (event == null) {
            return null;
        }
        return switch (event) {
            case AgentEvent.ToolCall call -> new AgentTraceStep("toolCall", call.toolName(), call.input());
            case AgentEvent.ToolResult result -> new AgentTraceStep("toolResult", result.toolName(), result.output());
            case AgentEvent.Hint hint -> new AgentTraceStep("hint", "", hint.text());
            case AgentEvent.Error error -> new AgentTraceStep("error", "", error.message());
            default -> null;
        };
    }
}
