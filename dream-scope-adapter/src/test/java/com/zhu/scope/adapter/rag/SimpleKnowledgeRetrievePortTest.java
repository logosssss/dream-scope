package com.zhu.scope.adapter.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.knowledge.RetrieveHit;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SimpleKnowledgeRetrievePortTest {

    @Test
    void retrieveFindsOverlappingTokensAndCapsTopK() {
        SimpleKnowledgeRetrievePort port = SimpleKnowledgeRetrievePort.create(new HashEmbeddingModel(64));
        port.addText("dream-scope 是基于 AgentScope 2.0 的模块化单体 Agent 运行时。", "intro", "intro");
        port.addText("对外 HTTP：POST /api/agents/invoke 同步调用。", "http", "intro");
        port.addText("生产会话走 Redis。", "redis", "intro");

        List<RetrieveHit> hits = port.retrieve("dream-scope", 1);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).text().contains("dream-scope"));
        assertEquals("intro", hits.get(0).source());
        assertEquals("intro", hits.get(0).docType());
        assertTrue(hits.get(0).id().startsWith("sk-"));
    }

    @Test
    void blankQueryOrNonPositiveTopKIsEmpty() {
        SimpleKnowledgeRetrievePort port = SimpleKnowledgeRetrievePort.create(new HashEmbeddingModel(32));
        port.addText("dream-scope 运行时", "intro", "intro");
        assertTrue(port.retrieve("  ", 3).isEmpty());
        assertTrue(port.retrieve("dream-scope", 0).isEmpty());
    }

    private static final class HashEmbeddingModel implements EmbeddingModel {

        private final int dims;

        HashEmbeddingModel(int dims) {
            this.dims = dims;
        }

        @Override
        public Mono<double[]> embed(ContentBlock block) {
            String text = "";
            if (block instanceof TextBlock tb && tb.getText() != null) {
                text = tb.getText();
            }
            double[] vector = new double[dims];
            for (String tok : text.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+")) {
                if (tok.length() < 2) {
                    continue;
                }
                vector[Math.floorMod(tok.hashCode(), dims)] += 1.0;
            }
            double norm = 0.0;
            for (double x : vector) {
                norm += x * x;
            }
            if (norm > 0.0) {
                double scale = Math.sqrt(norm);
                for (int i = 0; i < vector.length; i++) {
                    vector[i] /= scale;
                }
            }
            return Mono.just(vector);
        }

        @Override
        public String getModelName() {
            return "hash-test";
        }

        @Override
        public int getDimensions() {
            return dims;
        }
    }
}
