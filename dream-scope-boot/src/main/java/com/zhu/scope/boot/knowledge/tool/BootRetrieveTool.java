package com.zhu.scope.boot.knowledge.tool;

import com.zhu.scope.boot.knowledge.impl.BootKnowledgeAgent;
import com.zhu.scope.boot.knowledge.index.BootKnowledgeIndex;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 对话工具：从 boot 知识库取带编号的资料。只读，和 {@code agentId=knowledge} 共用索引。 */
public final class BootRetrieveTool {

    public static final String NAME = "retrieve";

    private static final int DEFAULT_TOP_K = 3;

    private static final int MAX_TOP_K = 8;

    private static final String MISS = "未检索到相关资料";

    private static final Logger log = LoggerFactory.getLogger(BootRetrieveTool.class);

    private final BootKnowledgeIndex index;

    public BootRetrieveTool(BootKnowledgeIndex index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @Tool(name = NAME, description = "从知识库检索带编号的参考资料。回答产品或调用方式前先调用，再按 [1][2] 引用", readOnly = true)
    public String retrieve(
            @ToolParam(name = "query", description = "检索问句，尽量包含专有名词", required = true) String query,
            @ToolParam(name = "topK", description = "返回条数，默认 3，上限 8", required = false) Integer topK) {
        int limit = topK == null || topK <= 0 ? DEFAULT_TOP_K : Math.min(topK, MAX_TOP_K);
        var hits = index.retrieve(query, limit);
        String formatted = BootKnowledgeAgent.format(hits);
        log.info("tool retrieve queryChars={} topK={} hits={}", query == null ? 0 : query.length(), limit, hits.size());
        return formatted.isEmpty() ? MISS : formatted;
    }
}
