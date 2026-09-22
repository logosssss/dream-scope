package com.zhu.scope.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentTraceTest {

    @Test
    void keepsToolHintAndErrorDropsText() {
        assertNull(AgentTrace.from(new AgentEvent.TextDelta("hi")));
        assertNull(AgentTrace.from(new AgentEvent.Done("done")));
        AgentTraceStep call = AgentTrace.from(new AgentEvent.ToolCall("retrieve", "{\"q\":\"a\"}"));
        AgentTraceStep result = AgentTrace.from(new AgentEvent.ToolResult("retrieve", "[1] hit"));
        AgentTraceStep hint = AgentTrace.from(new AgentEvent.Hint("先列步骤"));
        AgentTraceStep error = AgentTrace.from(new AgentEvent.Error("exceeded"));
        assertEquals(new AgentTraceStep("toolCall", "retrieve", "{\"q\":\"a\"}"), call);
        assertEquals(new AgentTraceStep("toolResult", "retrieve", "[1] hit"), result);
        assertEquals("", hint.name());
        assertEquals("先列步骤", hint.text());
        assertEquals("error", error.type());
    }

    @Test
    void resultCopiesTrace() {
        AgentInvokeResult result = new AgentInvokeResult(
                "chat",
                "ok",
                1,
                2,
                null,
                false,
                List.of(new AgentTraceStep("hint", "", "plan")));
        assertEquals(1, result.trace().size());
        assertTrue(new AgentInvokeResult("chat", "ok").trace().isEmpty());
    }
}
