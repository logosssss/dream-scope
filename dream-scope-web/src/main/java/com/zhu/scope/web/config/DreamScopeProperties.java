package com.zhu.scope.web.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private final NacosSettings nacos = new NacosSettings();

    @Getter
    public static class McpSettings {

        @Setter
    private boolean demoEnabled = true;

    @Setter
    private int demoPort = 8093;

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
    public static class NacosSettings {

        private boolean enabled = false;

        private String serverAddr = "127.0.0.1:8848";

        private String namespace = "public";

        private String username;

        private String password;

        private final PromptSettings prompt = new PromptSettings();

        private final A2aNacosSettings a2a = new A2aNacosSettings();

        private final SkillSettings skill = new SkillSettings();
    }

    @Getter
    @Setter
    public static class PromptSettings {

        private boolean enabled = false;

        private String sysPromptKey = "dream-scope-chat";

        private String version;

        private String label;

        private Map<String, String> variables = new LinkedHashMap<>();

        public void setVariables(Map<String, String> variables) {
            this.variables = variables == null ? new LinkedHashMap<>() : variables;
        }
    }

    @Getter
    @Setter
    public static class A2aNacosSettings {

        private boolean registryEnabled = false;

        private boolean discoveryEnabled = false;

        private String discoveryAgentName = "dream-scope-chat";

        private boolean registerAsLatest = true;

        private boolean registerEndpoint = true;

        private boolean streaming = false;
    }

    @Getter
    @Setter
    public static class SkillSettings {

        private boolean enabled = false;

        private List<String> names = new ArrayList<>();

        private String version;

        private String label;

        public void setNames(List<String> names) {
            this.names = names == null ? new ArrayList<>() : names;
        }
    }

    @Getter
    @Setter
    public static class RagSettings {

        /**
         * {@code auto}：配了 {@code dream-scope.rag.pg.jdbc-url} 且有模型 Key 用 pgvector；
         * 否则有 Key 用内存 SimpleKnowledge；无 Key 或 embedding 失败则关键词。
         * {@code pg} / {@code simple} / {@code keyword} 强制指定。
         */
        private String provider = "auto";

        /** DashScope embedding 模型名，与对话 {@code model.default} 分开。可写 {@code dashscope:text-embedding-v3}。 */
        private String embeddingModel = "text-embedding-v3";

        private int embeddingDimensions = 1024;

        /** 检索最低相似度，0～1。 */
        private double scoreThreshold = 0.3;

        /** Reader 切块大小（字符近似）。 */
        private int chunkSize = 2000;

        private int chunkOverlap = 200;

        /** 检索后精排。有 Key 走 DashScope，否则用词项精排。 */
        private boolean rerankEnabled = true;

        private String rerankModel = "gte-rerank-v2";

        /** 口语问句改写（多一次 LLM，默认关）。 */
        private boolean rewriteEnabled = false;

        /** 改写用模型；空则用对话 chat / default。 */
        private String rewriteModel = "";

        /** 精排前多取倍数，例如 3 表示先取 topK*3 再精排。 */
        private int candidateMultiplier = 3;

        private final PgSettings pg = new PgSettings();
    }

    @Getter
    @Setter
    public static class PgSettings {

        private String jdbcUrl;

        private String username = "postgres";

        private String password = "postgres";

        private String schema = "public";

        private String table = "dream_scope_rag";
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

        /** 当前主模型 Key。未填各家专用项时也作兜底。 */
        private String apiKey;

        /** 备用模型 Key；空则按 fallback 模型前缀再找各家 / api-key。 */
        private String fallbackApiKey;

        private String dashscopeApiKey;

        private String deepseekApiKey;

        private String openaiApiKey;

        private String anthropicApiKey;

        private String geminiApiKey;

        /** 绑定 {@code dream-scope.model.default} / {@code DREAM_SCOPE_MODEL_DEFAULT}。 */
        public String getDefault() {
            return defaultId;
        }

        public void setDefault(String defaultId) {
            this.defaultId = defaultId;
        }

        /** 按模型 id 前缀取 Key：先各家专用项，再 {@code api-key}。 */
        public String apiKeyFor(String modelId) {
            String specific = providerApiKey(modelId);
            if (hasText(specific)) {
                return specific.trim();
            }
            return hasText(apiKey) ? apiKey.trim() : null;
        }

        public String fallbackApiKeyFor(String fallbackModelId) {
            if (hasText(fallbackApiKey)) {
                return fallbackApiKey.trim();
            }
            return apiKeyFor(fallbackModelId);
        }

        private String providerApiKey(String modelId) {
            String id = modelId == null ? "" : modelId.trim().toLowerCase(Locale.ROOT);
            if (id.startsWith("deepseek:")) {
                return deepseekApiKey;
            }
            if (id.startsWith("openai:")) {
                return openaiApiKey;
            }
            if (id.startsWith("anthropic:")) {
                return anthropicApiKey;
            }
            if (id.startsWith("gemini:")) {
                return geminiApiKey;
            }
            return dashscopeApiKey;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
