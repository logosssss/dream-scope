package com.zhu.scope.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryAgentRegistryTest {

    @Test
    void findsRegisteredHandler() {
        AgentHandler echo = new EchoHandler();
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry(List.of(echo));
        assertTrue(registry.find(AgentIds.CHAT).isPresent());
        AgentInvokeResult result =
                registry.find(AgentIds.CHAT).orElseThrow().handle(new AgentInvokeRequest("", "s", "u", "hi"));
        assertEquals(AgentIds.CHAT, result.agentId());
        assertEquals("echo:hi", result.output());
    }

    @Test
    void unknownIdIsEmpty() {
        assertTrue(new InMemoryAgentRegistry(List.of()).find("nope").isEmpty());
    }

    @Test
    void blankIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryAgentRegistry(List.of(new AgentHandler() {
            @Override
            public String id() {
                return " ";
            }

            @Override
            public AgentInvokeResult handle(AgentInvokeRequest request) {
                return new AgentInvokeResult("", "");
            }
        })));
    }

    private static final class EchoHandler implements AgentHandler {
        @Override
        public String id() {
            return AgentIds.CHAT;
        }

        @Override
        public AgentInvokeResult handle(AgentInvokeRequest request) {
            return new AgentInvokeResult(id(), "echo:" + request.input());
        }
    }
}
