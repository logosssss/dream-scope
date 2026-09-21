package com.zhu.scope.web.config;

import com.zhu.scope.adapter.ScopeA2aClientAgent;
import com.zhu.scope.adapter.a2a.ScopeA2aServer;
import com.zhu.scope.adapter.nacos.ChatNacosClient;
import com.zhu.scope.agent.AgentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A2A Server 与可选客户端。不引入 a2a Spring starter。 */
@Configuration
public class A2aPortsConfig {

    private static final Logger log = LoggerFactory.getLogger(A2aPortsConfig.class);

    @Bean
    @ConditionalOnMissingBean
    ScopeA2aServer scopeA2aServer(
            @Qualifier("chatAgentHandler") AgentHandler chatAgentHandler,
            DreamScopeProperties props,
            ObjectProvider<ChatNacosClient> nacos) {
        ChatNacosClient client = nacos.getIfAvailable();
        boolean register = client != null && client.a2aRegistryEnabled();
        log.info("a2a server create publicUrl={} nacosRegistry={}", props.getA2a().getPublicUrl(), register);
        return ScopeA2aServer.create(chatAgentHandler, props.getA2a().getPublicUrl(), client);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> a2aPostEndpointReady(ScopeA2aServer server, DreamScopeProperties props) {
        return event -> {
            if (props.getA2a().isEnabled()) {
                log.info("a2a postEndpointReady publicUrl={}", props.getA2a().getPublicUrl());
                server.postEndpointReady();
            }
        };
    }

    @Bean(name = "a2aAgentHandler")
    @ConditionalOnBean(ChatNacosClient.class)
    @ConditionalOnProperty(prefix = "dream-scope.nacos.a2a", name = "discovery-enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "a2aAgentHandler")
    AgentHandler a2aNacosAgentHandler(ChatNacosClient nacos, DreamScopeProperties props) {
        String name = props.getNacos().getA2a().getDiscoveryAgentName();
        log.info("a2a client from nacos agent={}", name);
        return ScopeA2aClientAgent.fromNacos(nacos, name);
    }

    @Bean(name = "a2aAgentHandler")
    @ConditionalOnProperty(prefix = "dream-scope.a2a", name = "remote-url")
    @ConditionalOnMissingBean(name = "a2aAgentHandler")
    AgentHandler a2aAgentHandler(DreamScopeProperties props) {
        log.info("a2a client from well-known remoteUrl={}", props.getA2a().getRemoteUrl());
        return new ScopeA2aClientAgent(props.getA2a().getRemoteUrl());
    }
}
