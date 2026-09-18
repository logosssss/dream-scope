package com.zhu.scope.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AgentInvokeRequestTest {

    @Test
    void blankAgentIdDefaultsToChat() {
        AgentInvokeRequest req = new AgentInvokeRequest("  ", " s1 ", null, "你好");
        assertEquals(AgentIds.CHAT, req.agentId());
        assertEquals("s1", req.sessionId());
        assertEquals("", req.userId());
        assertTrue(req.hasInput());
    }

    @Test
    void blankInputIsNotHasInput() {
        assertFalse(new AgentInvokeRequest(AgentIds.CHAT, "s", "u", "  ").hasInput());
    }

    @Test
    void imageUrlsCountAsInput() {
        AgentInvokeRequest req =
                new AgentInvokeRequest(AgentIds.CHAT, "s", "u", "  ", List.of("https://example.com/a.png"));
        assertTrue(req.hasInput());
        assertEquals(List.of("https://example.com/a.png"), req.imageUrls());
    }

    @Test
    void rejectsNonHttpImageUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AgentInvokeRequest(AgentIds.CHAT, "s", "u", "看图", List.of("file:///tmp/a.png")));
    }

    @Test
    void jsonSchemaImpliesStructured() {
        AgentInvokeRequest req = new AgentInvokeRequest(
                AgentIds.CHAT,
                "s",
                "u",
                "天气",
                List.of(),
                false,
                Map.of("type", "object"));
        assertTrue(req.structured());
        assertEquals("object", req.jsonSchema().get("type"));
    }
}
