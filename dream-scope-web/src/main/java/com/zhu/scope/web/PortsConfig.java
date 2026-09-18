package com.zhu.scope.web;

import com.zhu.scope.adapter.ChatHarnessOptions;
import com.zhu.scope.adapter.ScopeChatAgent;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentRegistry;
import com.zhu.scope.agent.InMemoryAgentRegistry;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.InMemoryKeywordIndex;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 组合根：从 Spring {@code Environment} 读模型与超时，再交给 adapter。
 */
@Configuration
@EnableConfigurationProperties(DreamScopeProperties.class)
public class PortsConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(name = "chatAgentHandler")
    AgentHandler chatAgentHandler(DreamScopeProperties props, Environment env) {
        String modelId = ScopeChatAgent.resolveModelId(props.getModel().getChat(), props.getModel().getDefault());
        String apiKey = env.getProperty(ScopeChatAgent.apiKeyProperty(modelId));
        WorkspaceSubagentSeed.copyBundled(props.getWorkspaceDir());
        String fallbackId = props.getModel().getFallback();
        String fallbackKey =
                fallbackId == null || fallbackId.isBlank()
                        ? null
                        : env.getProperty(ScopeChatAgent.apiKeyProperty(fallbackId));
        ChatHarnessOptions options = new ChatHarnessOptions(
                props.getChatTimeout(),
                props.getWorkspaceDir(),
                props.getCompaction().getTriggerMessages(),
                props.getCompaction().getKeepMessages(),
                props.getRedis().getUri(),
                props.getRedis().getKeyPrefix(),
                props.getModel().getTemperature(),
                props.getModel().getTopP(),
                props.getModel().getMaxTokens(),
                fallbackId,
                fallbackKey);
        return ScopeChatAgent.create(modelId, apiKey, options);
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
