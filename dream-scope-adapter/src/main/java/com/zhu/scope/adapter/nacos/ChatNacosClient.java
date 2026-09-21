package com.zhu.scope.adapter.nacos;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import io.a2a.client.config.ClientConfig;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.a2a.agent.A2aAgentConfig;
import io.agentscope.core.a2a.server.registry.AgentRegistry;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.nacos.a2a.registry.NacosA2aRegistryProperties;
import io.agentscope.core.nacos.a2a.registry.NacosAgentRegistry;
import io.agentscope.core.nacos.prompt.NacosPromptListener;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import com.zhu.scope.adapter.LogText;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AgentScope 官方 Nacos AI 封装：Prompt 热更新 + A2A 注册/发现 + Skill ZIP。无 Spring。
 *
 * <p>web 只通过 {@link ChatNacosSettings} 进来。组合根 {@code PortsConfig}：{@code dream-scope.nacos.enabled=true}
 * 才 {@link #open}；bean {@code destroyMethod=close}。
 *
 * <h2>连的是哪条通道</h2>
 *
 * <p>{@link AiService} 走 Nacos <strong>3.x AI gRPC（默认 9848）</strong>。只开 8848 的 2.x 配置中心会连失败。
 * {@code SERVER_ADDR} 仍填 {@code host:8848}，客户端自己改 gRPC 口。{@link #open} 会 {@code getAgentCard} 探活；
 * 本客户端没有 {@code searchAgents}（RAD）。
 *
 * <h2>Prompt / Card / Skill 三份数据</h2>
 *
 * <ul>
 *   <li>{@link #sysPrompt} / {@link #promptMiddleware}：Prompt 资源名，不是 Group={@code agent} 的 Card。
 *   <li>{@link #discover} / {@link #a2aRegistry}：按 Agent 名精确 {@code getAgentCard}。
 *   <li>{@link #skillRepository}：{@code NacosSkillRepository} 下 ZIP，与本地 workspace {@code skills/} 并存。
 * </ul>
 */
public final class ChatNacosClient implements AutoCloseable {

    static final String AI_PROBE_AGENT = "__dream-scope-ai-probe__";

    private static final Logger log = LoggerFactory.getLogger(ChatNacosClient.class);

    private final ChatNacosSettings settings;

    private final AiService aiService;

    private final NacosPromptListener prompts;

    private final AgentRegistry a2aRegistry;

    private final NacosSkillRepository skillRepository;

    ChatNacosClient(ChatNacosSettings settings, AiService aiService) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.aiService = Objects.requireNonNull(aiService, "aiService");
        this.prompts = new NacosPromptListener(aiService);
        this.a2aRegistry = NacosAgentRegistry.builder(aiService)
                .nacosA2aProperties(NacosA2aRegistryProperties.builder()
                        .setAsLatest(settings.registerAsLatest())
                        .enabledRegisterEndpoint(settings.registerEndpoint())
                        .build())
                .build();
        this.skillRepository = settings.skillEnabled() ? newSkillRepository(aiService, settings) : null;
        log.info(
                "nacos client ready serverAddr={} namespace={} promptEnabled={} key={} a2aStreaming={} skillEnabled={} skillNames={}",
                settings.serverAddr(),
                settings.namespace(),
                settings.promptEnabled(),
                settings.sysPromptKey(),
                settings.a2aStreaming(),
                settings.skillEnabled(),
                settings.skillNames());
    }

    /**
     * 校验地址后建连，再用 {@link AiService#getAgentCard} 探 AI 通道。
     * Server 版本过低或 9848 不通会立刻失败，避免第一次对话才暴露。
     */
    public static ChatNacosClient open(ChatNacosSettings settings) {
        Objects.requireNonNull(settings, "settings");
        requireReady(settings);
        AiService ai;
        try {
            ai = AiFactory.createAiService(clientProperties(settings));
            log.info("nacos ai service created serverAddr={}", settings.serverAddr());
        } catch (NacosException ex) {
            throw new IllegalStateException("nacos ai client failed: " + settings.serverAddr(), ex);
        }
        try {
            probeAiChannel(ai, settings.serverAddr());
            return new ChatNacosClient(settings, ai);
        } catch (RuntimeException ex) {
            try {
                ai.shutdown();
            } catch (NacosException ignored) {
                // 探活失败时尽量关掉半开连接
            }
            throw ex;
        }
    }

    static void requireReady(ChatNacosSettings settings) {
        if (settings.serverAddr() == null || settings.serverAddr().isBlank()) {
            throw new IllegalArgumentException("nacos serverAddr required");
        }
        if (settings.namespace() == null || settings.namespace().isBlank()) {
            throw new IllegalArgumentException("nacos namespace required");
        }
    }

    /**
     * 探活。未知 Agent 的 NOT_FOUND 表示 gRPC 已通；501 / connection refused / version too low 视为致命。
     */
    static void probeAiChannel(AiService ai, String serverAddr) {
        try {
            ai.getAgentCard(AI_PROBE_AGENT);
            log.info("nacos ai probe ok serverAddr={} agent={} (card exists)", serverAddr, AI_PROBE_AGENT);
        } catch (NacosException ex) {
            if (isFatalAiProbe(ex)) {
                log.warn(
                        "nacos ai probe fatal serverAddr={} code={} msg={}",
                        serverAddr,
                        ex.getErrCode(),
                        ex.getErrMsg());
                throw new IllegalStateException(
                        "nacos ai grpc not ready (need Nacos 3.x with port 9848): " + serverAddr, ex);
            }
            log.info(
                    "nacos ai probe ready serverAddr={} code={} msg={} (unknown agent is ok)",
                    serverAddr,
                    ex.getErrCode(),
                    ex.getErrMsg());
        }
    }

    static boolean isFatalAiProbe(NacosException ex) {
        if (ex == null) {
            return false;
        }
        int code = ex.getErrCode();
        if (code == NacosException.NOT_FOUND || code == NacosException.RESOURCE_NOT_FOUND) {
            return false;
        }
        if (code == NacosException.SERVER_NOT_IMPLEMENTED
                || code == NacosException.BAD_GATEWAY
                || code == NacosException.CLIENT_DISCONNECT
                || code == NacosException.INVALID_SERVER_STATUS
                || code == NacosException.CLIENT_ERROR) {
            return true;
        }
        String msg = ex.getErrMsg() == null ? "" : ex.getErrMsg().toLowerCase(Locale.ROOT);
        return msg.contains("too low")
                || msg.contains("connection refused")
                || msg.contains("connection reset")
                || msg.contains("timed out")
                || msg.contains("timeout");
    }

    static Properties clientProperties(ChatNacosSettings settings) {
        requireReady(settings);
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
     * 拉 Prompt 正文。{@code PortsConfig} 不再把它写进 Harness {@code build()}；热更新走 {@link #promptMiddleware}。
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
                log.info("nacos prompt empty key={} fallbackChars={}", settings.sysPromptKey(), LogText.chars(fallback));
                return fallback;
            }
            log.info(
                    "nacos prompt loaded key={} version={} label={} chars={}",
                    settings.sysPromptKey(),
                    settings.promptVersion(),
                    settings.promptLabel(),
                    loaded.length());
            return loaded;
        } catch (NacosException ex) {
            log.warn("nacos prompt fallback key={}", settings.sysPromptKey(), ex);
            return fallback;
        }
    }

    /** Prompt 已开且有 key 时返回 {@link ChatNacosPromptMiddleware}，否则 {@code null}。 */
    public MiddlewareBase promptMiddleware() {
        if (!settings.promptEnabled() || settings.sysPromptKey() == null) {
            return null;
        }
        return new ChatNacosPromptMiddleware(this);
    }

    public boolean a2aRegistryEnabled() {
        return settings.a2aRegistryEnabled();
    }

    /** 本客户端单例 {@link NacosAgentRegistry}，不要每次 new。 */
    public AgentRegistry a2aRegistry() {
        return a2aRegistry;
    }

    public boolean a2aDiscoveryEnabled() {
        return settings.a2aDiscoveryEnabled() && settings.a2aDiscoveryAgentName() != null;
    }

    public String a2aDiscoveryAgentName() {
        return settings.a2aDiscoveryAgentName();
    }

    /**
     * {@link NacosAgentCardResolver} 按名拉 Card。是否 SSE 由 {@code dream-scope.nacos.a2a.streaming} 决定，默认关。
     */
    public A2aAgent discover(String agentName) {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("nacos a2a discovery agent name required");
        }
        NacosAgentCardResolver resolver = new NacosAgentCardResolver(aiService);
        log.info("nacos a2a discover agent={} streaming={}", agentName.trim(), settings.a2aStreaming());
        return A2aAgent.builder()
                .name(agentName.trim())
                .agentCardResolver(resolver)
                .a2aAgentConfig(A2aAgentConfig.builder()
                        .clientConfig(ClientConfig.builder().setStreaming(settings.a2aStreaming()).build())
                        .build())
                .build();
    }

    /** {@code dream-scope.nacos.skill.enabled} 时非空，给 {@code HarnessAgent.skillRepository}。 */
    public AgentSkillRepository skillRepository() {
        return skillRepository;
    }

    @Override
    public void close() {
        if (skillRepository != null) {
            skillRepository.close();
        }
        try {
            aiService.shutdown();
        } catch (NacosException ex) {
            log.warn("nacos ai shutdown failed", ex);
        }
    }

    private static NacosSkillRepository newSkillRepository(AiService aiService, ChatNacosSettings settings) {
        Properties extra = new Properties();
        if (settings.skillVersion() != null) {
            extra.setProperty(NacosSkillRepository.SKILL_VERSION_PATH, settings.skillVersion());
        }
        if (settings.skillLabel() != null) {
            extra.setProperty(NacosSkillRepository.SKILL_LABEL_PATH, settings.skillLabel());
        }
        log.info(
                "nacos skill repo namespace={} names={} version={} label={}",
                settings.namespace(),
                settings.skillNames(),
                settings.skillVersion(),
                settings.skillLabel());
        return new NacosSkillRepository(aiService, settings.namespace(), extra, settings.skillNames());
    }
}
