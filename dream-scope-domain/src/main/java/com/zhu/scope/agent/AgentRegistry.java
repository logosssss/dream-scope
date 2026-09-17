package com.zhu.scope.agent;

import java.util.Optional;

/**
 * 按 id 解析 Handler。web 只依赖本接口，不 new AgentScope 类型。
 */
public interface AgentRegistry {

    Optional<AgentHandler> find(String agentId);
}
