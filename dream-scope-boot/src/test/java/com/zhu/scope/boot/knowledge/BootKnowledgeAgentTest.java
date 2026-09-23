package com.zhu.scope.boot.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.zhu.scope.boot.agent.StarterHandler;
import com.zhu.scope.boot.knowledge.embed.BootEmbedder;
import com.zhu.scope.boot.knowledge.impl.BootKnowledgeAgent;
import com.zhu.scope.boot.knowledge.index.BootKnowledgeIndex;
import com.zhu.scope.boot.knowledge.store.BootPgKnowledge;
import com.zhu.scope.boot.agent.bean.request.StarterRequest;
import com.zhu.scope.boot.agent.event.StarterEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BootKnowledgeAgentTest {

    @Test
    void keywordHitIsCitedWithoutCallingAModel() {
        BootKnowledgeAgent agent = new BootKnowledgeAgent(BootKnowledgeIndex.seeded(null));
        var result = agent.handle(new StarterRequest(null, null, null, "端口 8092"));
        assertEquals("knowledge", result.agentId());
        assertTrue(result.output().startsWith("[1] "));
        assertTrue(result.output().contains("8092"));
    }

    @Test
    void missSaysNothingFound() {
        BootKnowledgeAgent agent = new BootKnowledgeAgent(BootKnowledgeIndex.seeded(null));
        var result = agent.handle(new StarterRequest(null, null, null, "qqqq zzzz"));
        assertEquals("未检索到相关资料", result.output());
    }

    @Test
    void vectorSupplementsWhenKeywordMisses() {
        BootEmbedder embedder = text -> text.startsWith("v") ? new float[] {1f, 0f} : new float[] {0f, 1f};
        BootKnowledgeIndex index = new BootKnowledgeIndex(embedder);
        index.add("vec", "v-only document");
        BootKnowledgeAgent agent = new BootKnowledgeAgent(index);
        var result = agent.handle(new StarterRequest(null, null, null, "v-question words"));
        assertTrue(result.output().contains("v-only document"));
    }

    @Test
    void vectorBelowThresholdIsDropped() {
        BootEmbedder embedder = text -> {
            if (text.startsWith("keep")) {
                return new float[] {1f, 0f};
            }
            if (text.startsWith("drop")) {
                return new float[] {0f, 1f};
            }
            return new float[] {1f, 0f};
        };
        BootKnowledgeIndex index = new BootKnowledgeIndex(embedder, null, 2000, 200, 0.3);
        index.add("keep-id", "keep-vector");
        index.add("drop-id", "drop-vector");
        var hits = index.retrieve("qqqq", 5);
        assertEquals(1, hits.size());
        assertEquals("keep-id", hits.get(0).id());
    }

    @Test
    void seedStaysInMemoryWithoutCallingVectorStore() {
        BootPgKnowledge pg = mock(BootPgKnowledge.class);
        BootKnowledgeIndex index = BootKnowledgeIndex.seeded(null, pg, 2000, 200, 0.3);
        verify(pg, never()).add(anyString(), anyString(), anyString());
        var hits = index.retrieve("8092", 5);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).text().contains("8092"));
    }

    @Test
    void keywordHitIgnoresVectorThreshold() {
        BootKnowledgeIndex index = new BootKnowledgeIndex(null, null, 2000, 200, 0.99);
        index.add("kw", "端口说明");
        var hits = index.retrieve("端口 aaaa bbbb", 5);
        assertEquals(1, hits.size());
        assertEquals("kw", hits.get(0).id());
    }

    @Test
    void streamEmitsTextThenDone() {
        BootKnowledgeAgent agent = new BootKnowledgeAgent(BootKnowledgeIndex.seeded(null));
        List<StarterEvent> events = new ArrayList<>();
        agent.streamHandle(new StarterRequest(null, null, null, "8092"), new StarterHandler.Sink() {
            @Override
            public void onEvent(StarterEvent event) {
                events.add(event);
            }

            @Override
            public void onComplete() {}

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof StarterEvent.TextDelta);
        assertTrue(events.get(1) instanceof StarterEvent.Done);
    }
}
