package com.zhu.scope.boot.knowledge.impl;

import com.zhu.scope.boot.agent.StarterHandler;
import com.zhu.scope.boot.knowledge.index.BootKnowledgeIndex;
import com.zhu.scope.boot.agent.bean.request.StarterRequest;
import com.zhu.scope.boot.agent.bean.response.StarterResult;
import com.zhu.scope.boot.agent.event.StarterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** agentId {@code knowledge}：只检索，不调对话模型。 */
public final class BootKnowledgeAgent implements StarterHandler {

    public static final String ID = "knowledge";

    static final int TOP_K = 5;

    private static final String MISS = "未检索到相关资料";

    private static final Logger log = LoggerFactory.getLogger(BootKnowledgeAgent.class);

    private final BootKnowledgeIndex index;

    public BootKnowledgeAgent(BootKnowledgeIndex index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public StarterResult handle(StarterRequest request) {
        String query = request == null || request.input() == null ? "" : request.input();
        List<BootKnowledgeIndex.Hit> hits = index.retrieve(query, TOP_K);
        String formatted = format(hits);
        String output = formatted.isEmpty() ? MISS : formatted;
        log.info("knowledge retrieve hits={} queryChars={}", hits.size(), query.length());
        return new StarterResult(id(), output);
    }

    @Override
    public void streamHandle(StarterRequest request, Sink sink) {
        Objects.requireNonNull(sink, "sink");
        StarterResult result = handle(request);
        sink.onEvent(new StarterEvent.TextDelta(result.output()));
        sink.onEvent(new StarterEvent.Done(result.output()));
        sink.onComplete();
    }

    public static String format(List<BootKnowledgeIndex.Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        List<String> blocks = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            blocks.add("[" + (i + 1) + "] " + hits.get(i).text());
        }
        return String.join("\n---\n", blocks);
    }
}
