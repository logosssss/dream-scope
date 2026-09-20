package com.zhu.scope.adapter.nacos;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import io.a2a.client.config.ClientConfig;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.A2aAgentConfig;
import io.agentscope.core.a2a.server.registry.AgentRegistry;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;
import io.agentscope.core.nacos.a2a.registry.NacosAgentRegistry;
import io.agentscope.core.nacos.prompt.NacosPromptListener;
import java.util.Objects;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 官方 Nacos AI 客户端：Prompt 热加载 + A2A 注册/发现。无 Spring。
 *
 * <p>{@link AiService} 连的是 Nacos 3.x AI gRPC（默认 9848），不是只开 8848 的 2.x 配置中心。
 */
public final class ChatNacosClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChatNacosClient.class);

    private final ChatNacosSettings settings;

    private final AiService aiService;

    private final NacosPromptListener prompts;

    ChatNacosClient(ChatNacosSettings settings, AiService aiService) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.aiService = Objects.requireNonNull(aiService, "aiService");
        this.prompts = new NacosPromptListener(aiService);
    }

    public static ChatNacosClient open(ChatNacosSettings settings) {
        Objects.requireNonNull(settings, "settings");
        try {
            return new ChatNacosClient(settings, AiFactory.createAiService(clientProperties(settings)));
        } catch (NacosException ex) {
            throw new IllegalStateException("nacos ai client failed: " + settings.serverAddr(), ex);
        }
    }

    static Properties clientProperties(ChatNacosSettings settings) {
        Properties props = new Properties();
        props.setProperty(PropertyKeyConst.SERVER_ADDR, settings.serverAddr());
        props.setProperty(PropertyKeyConst.NAMESPACE, settings.namespace());
        if (settings.username() != null) {
            props.setProperty(PropertyKeyConst.USERNAME, settings.username());
        }
        if (settings.password() != null) {
            props.setProperty(PropertyKeyConst.PASSWORD, settings.password());
        }
        return props;
    }

    /**
     * 启动时拉系统提示。Nacos 没有该 key 或失败时回落到 {@code fallback}（与官方 starter 一致）。
     * Harness {@code sysPrompt} 是 build 时写入的，之后 Nacos 再改模板需要重启进程。
     */
    public String sysPrompt(String fallback) {
        if (!settings.promptEnabled() || settings.sysPromptKey() == null) {
            return fallback;
        }
        try {
            String loaded = prompts.getPrompt(
                    settings.sysPromptKey(),
                    settings.promptVersion(),
                    settings.promptLabel(),
                    settings.promptVariables(),
                    fallback);
            if (loaded == null || loaded.isBlank()) {
                return fallback;
            }
            log.info("loaded nacos sys prompt key={}", settings.sysPromptKey());
            return loaded;
        } catch (NacosException ex) {
            log.warn("nacos prompt fallback key={}", settings.sysPromptKey(), ex);
            return fallback;
        }
    }

    public boolean a2aRegistryEnabled() {
        return settings.a2aRegistryEnabled();
    }

    public AgentRegistry a2aRegistry() {
        return NacosAgentRegistry.builder(aiService)
                .nacosA2aProperties(NacosA2aRegistryProperties.builder()
                        .setAsLatest(settings.registerAsLatest())
                        .enabledRegisterEndpoint(settings.registerEndpoint())
                        .build())
                .build();
    }

    public boolean a2aDiscoveryEnabled() {
        return settings.a2aDiscoveryEnabled() && settings.a2aDiscoveryAgentName() != null;
    }

    public String a2aDiscoveryAgentName() {
        return settings.a2aDiscoveryAgentName();
    }

    /** 用 Nacos AgentCard 发现远端，{@code A2aAgent.name} 必须等于 Nacos 里的 Agent 名。 */
    public A2aAgent discover(String agentName) {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("nacos a2a discovery agent name required");
        }
        NacosAgentCardResolver resolver = new NacosAgentCardResolver(aiService);
        return A2aAgent.builder()
                .name(agentName.trim())
                .agentCardResolver(resolver)
                .a2aAgentConfig(A2aAgentConfig.builder()
                        .clientConfig(ClientConfig.builder().setStreaming(false).build())
                        .build())
                .build();
    }

    @Override
    public void close() {
        try {
            aiService.shutdown();
        } catch (NacosException ex) {
            log.warn("nacos ai shutdown failed", ex);
        }
    }
}
