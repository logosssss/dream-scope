package com.zhu.scope.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhu.scope.adapter.rag.SimpleKnowledgeRetrievePort;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.AdvancedRetrievePort;
import com.zhu.scope.rag.HybridRetrievePort;
import com.zhu.scope.rag.InMemoryKeywordIndex;
import com.zhu.scope.web.config.DreamScopeProperties;
import com.zhu.scope.web.config.PortsConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PortsConfigRagTest {

    @Test
    void autoWithoutKeyUsesKeywordIndex() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setRerankEnabled(false);
        RetrievePort port = PortsConfig.createRetrievePort(props, key -> {
            throw new AssertionError("simple factory must not run");
        });
        assertInstanceOf(InMemoryKeywordIndex.class, port);
        assertFalse(port.retrieve("Redis", 3).isEmpty());
    }

    @Test
    void autoFallsBackToKeywordWhenSimpleIngestFails() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setRerankEnabled(false);
        props.getModel().setApiKey("sk-test");
        RetrievePort port = PortsConfig.createRetrievePort(props, key -> {
            throw new IllegalStateException(
                    "simple knowledge ingest failed", new RuntimeException("Free quota exhausted"));
        });
        assertInstanceOf(InMemoryKeywordIndex.class, port);
        assertTrue(port.retrieve("dream-scope", 3).stream().anyMatch(hit -> hit.text().contains("AgentScope")));
    }

    @Test
    void simpleProviderDoesNotSwallowIngestFailure() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setProvider("simple");
        props.getModel().setApiKey("sk-test");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> PortsConfig.createRetrievePort(props, key -> {
                    throw new IllegalStateException("simple knowledge ingest failed");
                }));
        assertTrue(ex.getMessage().contains("simple knowledge ingest failed"));
    }

    @Test
    void apiKeyForPrefersProviderThenGeneric() {
        DreamScopeProperties.ModelSettings model = new DreamScopeProperties().getModel();
        assertNull(model.apiKeyFor("dashscope:qwen-plus"));
        model.setApiKey("sk-generic");
        assertEquals("sk-generic", model.apiKeyFor("dashscope:qwen-plus"));
        assertEquals("sk-generic", model.apiKeyFor("deepseek:deepseek-chat"));
        model.setDeepseekApiKey("sk-ds");
        assertEquals("sk-ds", model.apiKeyFor("deepseek:deepseek-chat"));
        model.setFallbackApiKey("sk-fb");
        assertEquals("sk-fb", model.fallbackApiKeyFor("dashscope:qwen-turbo"));
    }

    @Test
    void pgWithoutJdbcUrlFailsWhenForced() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setProvider("pg");
        props.getModel().setApiKey("sk-test");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> PortsConfig.createRetrievePort(props, key -> {
                    throw new AssertionError("simple factory must not run");
                }));
        assertTrue(ex.getMessage().contains("jdbc-url"));
    }

    @Test
    void autoPgFailureFallsBackToKeyword() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setRerankEnabled(false);
        props.getModel().setApiKey("sk-test");
        props.getRag().getPg().setJdbcUrl("jdbc:postgresql://127.0.0.1:5432/dream_scope");
        RetrievePort port = PortsConfig.createRetrievePort(
                props,
                key -> {
                    throw new IllegalStateException(
                            "simple knowledge ingest failed", new RuntimeException("skip memory"));
                },
                ignored -> {
                    throw new IllegalStateException("pg connect failed", new RuntimeException("connection refused"));
                });
        assertInstanceOf(InMemoryKeywordIndex.class, port);
    }

    @Test
    void pgProviderDoesNotSwallowConnectFailure() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setProvider("pg");
        props.getModel().setApiKey("sk-test");
        props.getRag().getPg().setJdbcUrl("jdbc:postgresql://127.0.0.1:5432/dream_scope");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> PortsConfig.createRetrievePort(
                        props,
                        key -> {
                            throw new AssertionError("simple factory must not run");
                        },
                        ignored -> {
                            throw new IllegalStateException("pg down");
                        }));
        assertTrue(ex.getMessage().contains("pg down"));
    }

    @Test
    void pgSeedQuotaFallsBackToKeyword() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setRerankEnabled(false);
        props.getRag().setProvider("pg");
        props.getModel().setApiKey("sk-test");
        props.getRag().getPg().setJdbcUrl("jdbc:postgresql://127.0.0.1:5432/dream_scope");
        RetrievePort port = PortsConfig.createRetrievePort(
                props,
                key -> {
                    throw new AssertionError("simple factory must not run");
                },
                ignored -> {
                    throw new IllegalStateException(
                            "simple knowledge ingest failed",
                            new RuntimeException(
                                    "Free quota exhausted. AllocationQuota.FreeTierOnly"));
                });
        assertInstanceOf(InMemoryKeywordIndex.class, port);
        assertFalse(port.retrieve("Redis", 3).isEmpty());
    }

    @Test
    void ragEmbeddingSettingsComeFromProperties() {
        DreamScopeProperties props = new DreamScopeProperties();
        assertEquals("text-embedding-v3", PortsConfig.embeddingSettings(props).modelName());
        assertEquals(1024, PortsConfig.embeddingSettings(props).dimensions());
        props.getRag().setEmbeddingModel("dashscope:text-embedding-v2");
        props.getRag().setEmbeddingDimensions(768);
        assertEquals("text-embedding-v2", PortsConfig.embeddingSettings(props).modelName());
        assertEquals(768, PortsConfig.embeddingSettings(props).dimensions());
    }

    @Test
    void embeddingFailureDetectsDashScopeQuota() {
        assertTrue(PortsConfig.isEmbeddingFailure(new RuntimeException(
                "Failed to generate embedding: AllocationQuota.FreeTierOnly")));
        assertFalse(PortsConfig.isEmbeddingFailure(new IllegalStateException("pg down")));
    }

    @Test
    void pgSkipsSeedWhenStoreAlreadyHasDocuments() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setProvider("pg");
        props.getModel().setApiKey("sk-test");
        props.getRag().getPg().setJdbcUrl("jdbc:postgresql://127.0.0.1:5432/dream_scope");
        SimpleKnowledgeRetrievePort existing = Mockito.mock(SimpleKnowledgeRetrievePort.class);
        when(existing.hasStoredDocuments()).thenReturn(true);
        when(existing.rebuildSourceIndexFromStore()).thenReturn(3);
        RetrievePort port = PortsConfig.createRetrievePort(
                props,
                key -> {
                    throw new AssertionError("simple factory must not run");
                },
                ignored -> existing);
        assertInstanceOf(AdvancedRetrievePort.class, port);
        verify(existing).rebuildSourceIndexFromStore();
        verify(existing).storedChunks();
        verify(existing, never()).addText(any(), any(), any(), any());
    }

    @Test
    void simpleSuccessWrapsHybrid() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setProvider("simple");
        props.getModel().setApiKey("sk-test");
        SimpleKnowledgeRetrievePort existing = Mockito.mock(SimpleKnowledgeRetrievePort.class);
        when(existing.hasStoredDocuments()).thenReturn(true);
        when(existing.rebuildSourceIndexFromStore()).thenReturn(0);
        RetrievePort port = PortsConfig.createRetrievePort(props, key -> existing);
        assertInstanceOf(AdvancedRetrievePort.class, port);
        verify(existing).storedChunks();
        verify(existing, never()).addText(any(), any(), any(), any());
    }

    @Test
    void enhanceAddsLexicalRerankWithoutApiKey() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getRag().setRerankEnabled(true);
        RetrievePort port = PortsConfig.enhance(new InMemoryKeywordIndex(), props);
        assertInstanceOf(AdvancedRetrievePort.class, port);
    }
}
