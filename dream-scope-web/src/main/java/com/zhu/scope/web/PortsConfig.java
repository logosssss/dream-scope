package com.zhu.scope.web;

import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentRegistry;
import com.zhu.scope.agent.InMemoryAgentRegistry;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.InMemoryKeywordIndex;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动期接线：先挂 echo Handler，AgentScope ReAct 装配后续进 adapter。
 */
@Configuration
public class PortsConfig {

    @Bean
    AgentHandler echoChatHandler() {
        return new AgentHandler() {
            @Override
            public String id() {
                return AgentIds.CHAT;
            }

            @Override
            public AgentInvokeResult handle(AgentInvokeRequest request) {
                return new AgentInvokeResult(id(), "echo:" + request.input());
            }
        };
    }

    @Bean
    AgentRegistry agentRegistry(List<AgentHandler> handlers) {
        return new InMemoryAgentRegistry(handlers);
    }

    @Bean
    RetrievePort retrievePort() {
        InMemoryKeywordIndex index = new InMemoryKeywordIndex();
        index.ingest("dream-scope 是基于 AgentScope 2.0 的模块化单体 Agent 运行时。", "intro", "intro");
        return index;
    }
}
