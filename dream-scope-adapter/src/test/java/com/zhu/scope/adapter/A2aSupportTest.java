package com.zhu.scope.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class A2aSupportTest {

    @Test
    void agentCardPointsToJsonRpcPath() {
        Map<String, Object> card = A2aSupport.agentCard("http://127.0.0.1:8091/");
        assertEquals("dream-scope-chat", card.get("name"));
        assertEquals("http://127.0.0.1:8091/a2a", card.get("url"));
        assertEquals("JSONRPC", card.get("preferredTransport"));
    }

    @Test
    void messageSendReturnsCompletedArtifact() {
        Map<String, Object> response = A2aSupport.handleJsonRpc(A2aSupport.requestJson("你好"), text -> "回:" + text);
        assertEquals("回:你好", A2aSupport.outputOf(response));
        assertEquals("1", String.valueOf(response.get("id")));
    }

    @Test
    void unknownMethodIsJsonRpcError() {
        Map<String, Object> response = A2aSupport.handleJsonRpc(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tasks/get\"}", text -> text);
        assertTrue(response.containsKey("error"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertEquals(-32601, error.get("code"));
    }
}
