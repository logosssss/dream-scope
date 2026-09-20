package com.zhu.scope.web;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 {@code dream-scope.*}；环境变量 {@code DREAM_SCOPE_MODEL_CHAT} 等经 relaxed binding 进来。
 */
@Getter
@ConfigurationProperties(prefix = "dream-scope")
public class DreamScopeProperties {

    private final ModelSettings model = new ModelSettings();

    @Setter
    private Duration chatTimeout = Duration.ofSeconds(120);

    @Setter
    private Path workspaceDir = Path.of(".agentscope/workspace");

    private final RedisSettings redis = new RedisSettings();

    private final CompactionSettings compaction = new CompactionSettings();

    private final PlanModeSettings planMode = new PlanModeSettings();

    private final McpSettings mcp = new McpSettings();

    private final A2aSettings a2a = new A2aSettings();

    private final RagSettings rag = new RagSettings();

    @Getter
    public static class McpSettings {

        private List<McpServerSettings> servers = new ArrayList<>();

        public void setServers(List<McpServerSettings> servers) {
            this.servers = servers == null ? new ArrayList<>() : servers;
        }
    }

    @Getter
    @Setter
    public static class McpServerSettings {

        private String name;

        private String transport = "streamableHttp";

        private String url;

        private String command;

        private List<String> args = new ArrayList<>();

        private Map<String, String> env = new LinkedHashMap<>();

        private Map<String, String> headers = new LinkedHashMap<>();

        private Duration timeout;

        public void setArgs(List<String> args) {
            this.args = args == null ? new ArrayList<>() : args;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env == null ? new LinkedHashMap<>() : env;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : headers;
        }
    }

    @Getter
    @Setter
    public static class RagSettings {

        /**
         * {@code auto}：有 {@code DASHSCOPE_API_KEY} 用官方 SimpleKnowledge，否则关键词。
         * {@code simple} / {@code keyword} 强制指定。
         */
        private String provider = "auto";
    }

    @Getter
    @Setter
    public static class A2aSettings {

        private boolean enabled = true;

        private String publicUrl = "http://127.0.0.1:8091";

        private String remoteUrl;
    }

    @Getter
    @Setter
    public static class PlanModeSettings {

        private boolean enabled = true;

        private String directory = "plans";
    }

    @Getter
    @Setter
    public static class CompactionSettings {

        private int triggerMessages = 30;

        private int keepMessages = 10;
    }

    @Getter
    @Setter
    public static class RedisSettings {

        private String uri = "redis://127.0.0.1:6379";

        private String keyPrefix = "dream-scope:";
    }

    @Getter
    @Setter
    public static class ModelSettings {

        private String chat;

        @Getter(AccessLevel.NONE)
        @Setter(AccessLevel.NONE)
        private String defaultId;

        private Double temperature;

        private Double topP;

        private Integer maxTokens;

        private String fallback;

        /** 绑定 {@code dream-scope.model.default} / {@code DREAM_SCOPE_MODEL_DEFAULT}。 */
        public String getDefault() {
            return defaultId;
        }

        public void setDefault(String defaultId) {
            this.defaultId = defaultId;
        }
    }
}
