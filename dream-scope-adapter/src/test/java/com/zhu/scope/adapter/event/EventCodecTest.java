package com.zhu.scope.adapter.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.agent.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatUsage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EventCodecTest {

    @Test
    void mapsTextDeltaAndDone() {
        EventCodec codec = new EventCodec();
        assertEquals(
                Optional.of(new AgentEvent.TextDelta("你好")),
                codec.toDomain(new TextBlockDeltaEvent("r1", "b1", "你好")));
        assertTrue(codec.toDomain(new TextBlockDeltaEvent("r1", "b1", "")).isEmpty());
        assertEquals(
                Optional.of(new AgentEvent.Done("最终回复")),
                codec.toDomain(new AgentResultEvent(new UserMessage("最终回复"))));
    }

    @Test
    void accumulatesUsageOntoDone() {
        EventCodec codec = new EventCodec();
        assertTrue(codec.toDomain(new ModelCallEndEvent("r1", new ChatUsage(3, 5, 0))).isEmpty());
        assertTrue(codec.toDomain(new ModelCallEndEvent("r2", new ChatUsage(1, 2, 0))).isEmpty());
        assertEquals(
                Optional.of(new AgentEvent.Done("最终回复", 4, 7)),
                codec.toDomain(new AgentResultEvent(new UserMessage("最终回复"))));
    }

    @Test
    void accumulatesToolCallInputUntilEnd() {
        EventCodec codec = new EventCodec();
        assertTrue(codec.toDomain(new ToolCallStartEvent("r1", "c1", "calculate")).isEmpty());
        assertTrue(codec.toDomain(new ToolCallDeltaEvent("r1", "c1", "calculate", "{\"expression\":")).isEmpty());
        assertTrue(codec.toDomain(new ToolCallDeltaEvent("r1", "c1", "calculate", "\"1+2\"}")).isEmpty());
        assertEquals(
                Optional.of(new AgentEvent.ToolCall("calculate", "{\"expression\":\"1+2\"}")),
                codec.toDomain(new ToolCallEndEvent("r1", "c1", "calculate")));
    }

    @Test
    void accumulatesToolResultOutputUntilEnd() {
        EventCodec codec = new EventCodec();
        assertTrue(codec.toDomain(new ToolResultTextDeltaEvent("r1", "c1", "calculate", "7")).isEmpty());
        assertEquals(
                Optional.of(new AgentEvent.ToolResult("calculate", "7")),
                codec.toDomain(new ToolResultEndEvent("r1", "c1", "calculate", ToolResultState.SUCCESS)));
    }

    @Test
    void mapsExceedMaxItersToErrorAndIgnoresLifecycle() {
        EventCodec codec = new EventCodec();
        assertEquals(
                Optional.of(new AgentEvent.Error("exceeded max iterations: 10/10")),
                codec.toDomain(new ExceedMaxItersEvent("r1", 10, 10)));
        assertTrue(codec.toDomain(new AgentStartEvent("s1", "r1", "chat")).isEmpty());
        assertTrue(codec.toDomain(null).isEmpty());
    }
}
