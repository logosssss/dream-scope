package com.zhu.scope.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.InMemoryKeywordIndex;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PortsConfigRagTest {

    @Test
    void autoWithoutKeyUsesKeywordIndex() {
        DreamScopeProperties props = new DreamScopeProperties();
        RetrievePort port = PortsConfig.createRetrievePort(props, new MockEnvironment(), key -> {
            throw new AssertionError("simple factory must not run");
        });
        assertInstanceOf(InMemoryKeywordIndex.class, port);
        assertFalse(port.retrieve("Redis", 3).isEmpty());
    }

    @Test
    void autoFallsBackToKeywordWhenSimpleIngestFails() {
        DreamScopeProperties props = new DreamScopeProperties();
        MockEnvironment env = new MockEnvironment().withProperty("DASHSCOPE_API_KEY", "sk-test");
        RetrievePort port = PortsConfig.createRetrievePort(props, env, key -> {
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
        MockEnvironment env = new MockEnvironment().withProperty("DASHSCOPE_API_KEY", "sk-test");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> PortsConfig.createRetrievePort(props, env, key -> {
                    throw new IllegalStateException("simple knowledge ingest failed");
                }));
        assertTrue(ex.getMessage().contains("simple knowledge ingest failed"));
    }
}
