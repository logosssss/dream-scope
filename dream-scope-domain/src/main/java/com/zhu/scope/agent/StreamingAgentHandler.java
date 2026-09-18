package com.zhu.scope.agent;

/**
 * 支持事件流的 Agent。{@link #handle} 仍是同步语义。
 */
public interface StreamingAgentHandler extends AgentHandler {

    void streamHandle(AgentInvokeRequest request, AgentStreamHandler handler);
}
