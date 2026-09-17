package com.zhu.scope.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
