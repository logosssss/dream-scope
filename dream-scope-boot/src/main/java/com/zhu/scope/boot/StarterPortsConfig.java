package com.zhu.scope.boot;

import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentRegistry;
import com.zhu.scope.agent.InMemoryAgentRegistry;
import io.agentscope.core.ReActAgent;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把官方 {@link ReActAgent} 登记进 domain 注册表。Bean 由 starter 自动配置提供。
 */
@Configuration
@EnableConfigurationProperties(BootScopeProperties.class)
public class StarterPortsConfig {

    @Bean
    @ConditionalOnProperty(prefix = "agentscope.agent", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "starterChatAgent")
    AgentHandler starterChatAgent(ReActAgent agentscopeReActAgent, BootScopeProperties props) {
        return new StarterChatAgent(agentscopeReActAgent, props.getChatTimeout());
    }

    @Bean
    AgentRegistry agentRegistry(List<AgentHandler> handlers) {
        return new InMemoryAgentRegistry(handlers);
    }
}
