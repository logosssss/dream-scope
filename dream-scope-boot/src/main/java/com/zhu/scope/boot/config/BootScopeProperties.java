package com.zhu.scope.boot.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "dream-scope")
public class BootScopeProperties {

    private final Mcp mcp = new Mcp();

    private final Plan plan = new Plan();

    private final Knowledge knowledge = new Knowledge();

    private final Redis redis = new Redis();

    private final Compaction compaction = new Compaction();

    private final Eviction eviction = new Eviction();

    private final Skills skills = new Skills();

    private final ChatModel model = new ChatModel();

    private Duration chatTimeout = Duration.ofSeconds(120);

    public void setChatTimeout(Duration chatTimeout) {
        this.chatTimeout = chatTimeout == null || chatTimeout.isZero() || chatTimeout.isNegative()
                ? Duration.ofSeconds(120)
                : chatTimeout;
    }

    @Getter
    @Setter
    public static class Mcp {

        /** 本进程内的演示 MCP。关掉则沿用 starter 的空 Toolkit。 */
        private boolean demoEnabled = true;

        /** 避开 web 演示用的 8093。 */
        private int demoPort = 8094;
    }

    @Getter
    @Setter
    public static class Plan {

        /** 计划文件所在目录，相对 workspace。 */
        private String directory = "plans";

        /** 单轮最多调用模型的次数。≤0 时用 10。 */
        private int maxIters = 10;

        /** 空则用临时目录下的 dream-scope-boot/workspace。 */
        private String workspace = "";
    }

    @Getter
    @Setter
    public static class Redis {

        /** 必填。空或连不上则带对话的进程起不来。 */
        private String uri = "redis://127.0.0.1:6379";

        /** 与 web 的 dream-scope: 分开，避免两个进程写同一组键。 */
        private String keyPrefix = "dream-scope-boot:";
    }

    @Getter
    @Setter
    public static class Compaction {

        /** 会话消息达到这个条数后压缩。≤0 时用 30。 */
        private int triggerMessages = 30;

        /** 压缩后保留的最近消息条数。≤0 时用 10。 */
        private int keepMessages = 10;
    }

    @Getter
    @Setter
    public static class Eviction {

        /** 单条工具结果超过这个字符数就截断。≤0 时用 80000。 */
        private int maxResultChars = 80000;

        /** 截断后留在对话里的预览字符数。≤0 时用 2000。 */
        private int previewChars = 2000;

        /** 全文落在工作区下的这个目录，用 read_file 读回。 */
        private String path = "large_tool_results";
    }

    @Getter
    @Setter
    public static class ChatModel {

        /** 空则主模型失败不切换。与主模型同名则忽略。 */
        private String fallback = "";

        /** 空则用 agentscope.dashscope.api-key。 */
        private String fallbackApiKey = "";

        /** 空则不写入 GenerateOptions，沿用模型默认。须在 [0, 2]。 */
        private Double temperature;

        /** 空则不写。须在 (0, 1]。 */
        private Double topP;

        /** 空则不写。须为正数。 */
        private Integer maxTokens;
    }

    @Getter
    @Setter
    public static class Skills {

        /** 相对 workspace。每个子目录一份 SKILL.md。 */
        private String directory = "skills";
    }

    @Getter
    @Setter
    public static class Knowledge {

        /** 空则只做关键词检索。配了 PG 时用它做向量；没配 PG 时用内存余弦补关键词没命中的条目。 */
        private String apiKey = "";

        private String embeddingModel = "text-embedding-v3";

        private int embeddingDimensions = 1024;

        private int chunkSize = 2000;

        private int chunkOverlap = 200;

        /** 向量补召回的最低分。关键词命中不受这个门槛影响。 */
        private double scoreThreshold = 0.3;

        private final Pg pg = new Pg();

        @Getter
        @Setter
        public static class Pg {

            /** 空则不连 Postgres。 */
            private String jdbcUrl = "";

            private String username = "postgres";

            private String password = "postgres";

            private String schema = "public";

            /** 与 web 的 dream_scope_rag 分开，避免两个进程写同一张表。 */
            private String table = "dream_scope_boot_rag";
        }
    }
}
