package com.zhu.scope.web.config;

import com.zhu.scope.adapter.ScopeKnowledgeAgent;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentRegistry;
import com.zhu.scope.agent.InMemoryAgentRegistry;
import com.zhu.scope.knowledge.RetrievePort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 把容器里的 {@link AgentHandler} 收成注册表。 */
@Configuration
public class AgentRegistryConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistryConfig.class);

    @Bean
    @ConditionalOnMissingBean(name = "knowledgeAgentHandler")
    AgentHandler knowledgeAgentHandler(RetrievePort retrievePort) {
        return new ScopeKnowledgeAgent(retrievePort);
    }

    @Bean
    AgentRegistry agentRegistry(List<AgentHandler> handlers) {
        List<String> ids = handlers.stream().map(AgentHandler::id).toList();
        log.info("agent registry ids={}", ids);
        return new InMemoryAgentRegistry(handlers);
    }
}
