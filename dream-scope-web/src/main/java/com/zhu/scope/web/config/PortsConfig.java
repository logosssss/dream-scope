package com.zhu.scope.web.config;

import com.zhu.scope.adapter.nacos.ChatNacosSettings;
import com.zhu.scope.adapter.rag.ChatEmbeddingSettings;
import com.zhu.scope.adapter.rag.SimpleKnowledgeRetrievePort;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentRegistry;
import com.zhu.scope.knowledge.RetrievePort;
import java.util.function.Function;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 产品入口（8091）组合根。具体装配拆到 Chat / Nacos / A2A / RAG / 注册表，本类只负责把它们收在一起。
 *
 * <p>Controller 只认 {@link AgentHandler} / {@link AgentRegistry} / {@link RetrievePort}，不碰
 * {@code io.agentscope}。官方 starter 在 {@code dream-scope-boot}，不经过这里。
 *
 * <p>{@code chatAgentHandler} / {@code knowledgeAgentHandler} / {@code a2aAgentHandler} 带
 * {@code ConditionalOnMissingBean}：web 单测用同名 {@code @TestBean} 换桩。
 */
@Configuration
@EnableConfigurationProperties(DreamScopeProperties.class)
@Import({
    ChatPortsConfig.class,
    NacosPortsConfig.class,
    A2aPortsConfig.class,
    RagPortsConfig.class,
    AgentRegistryConfig.class
})
public class PortsConfig {

    public static RetrievePort createRetrievePort(
            DreamScopeProperties props, Function<String, SimpleKnowledgeRetrievePort> simpleFactory) {
        return RagPortsConfig.createRetrievePort(props, simpleFactory);
    }

    public static RetrievePort createRetrievePort(
            DreamScopeProperties props,
            Function<String, SimpleKnowledgeRetrievePort> simpleFactory,
            Function<DreamScopeProperties, SimpleKnowledgeRetrievePort> pgFactory) {
        return RagPortsConfig.createRetrievePort(props, simpleFactory, pgFactory);
    }

    public static RetrievePort enhance(RetrievePort port, DreamScopeProperties props) {
        return RagPortsConfig.enhance(port, props);
    }

    public static boolean isEmbeddingFailure(Throwable error) {
        return RagPortsConfig.isEmbeddingFailure(error);
    }

    public static ChatEmbeddingSettings embeddingSettings(DreamScopeProperties props) {
        return RagPortsConfig.embeddingSettings(props);
    }

    public static ChatNacosSettings nacosSettings(DreamScopeProperties props) {
        return NacosPortsConfig.nacosSettings(props);
    }
}
