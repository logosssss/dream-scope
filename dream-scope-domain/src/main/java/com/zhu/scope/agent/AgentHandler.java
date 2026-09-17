package com.zhu.scope.agent;

/**
 * 业务 Agent 入口。实现放 adapter（AgentScope）或测试桩；domain 不碰框架。
 */
public interface AgentHandler {

    String id();

    AgentInvokeResult handle(AgentInvokeRequest request);
}
