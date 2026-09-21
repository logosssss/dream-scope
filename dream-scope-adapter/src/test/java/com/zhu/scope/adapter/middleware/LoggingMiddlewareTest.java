package com.zhu.scope.adapter.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.Model;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

class LoggingMiddlewareTest {

    @Test
    void onAgentLogsCompleteWithAgentAndSession() {
        Logger log = mock(Logger.class);
        Agent agent = mock(Agent.class);
        when(agent.getAgentId()).thenReturn("chat");
        RuntimeContext ctx = RuntimeContext.builder().sessionId("s1").build();
        AtomicInteger nextCalls = new AtomicInteger();
        AgentInput input = new AgentInput(List.of());

        new LoggingMiddleware(log)
                .onAgent(agent, ctx, input, ignored -> {
                    nextCalls.incrementAndGet();
                    return Flux.empty();
                })
                .blockLast();

        assertEquals(1, nextCalls.get());
        verify(log).info("chat middleware onAgent start agentId={} sessionId={}", "chat", "s1");
        verify(log)
                .info(
                        eq("chat middleware onAgent complete agentId={} sessionId={} elapsedMs={}"),
                        eq("chat"),
                        eq("s1"),
                        anyLong());
    }

    @Test
    void onModelCallLogsModelName() {
        Logger log = mock(Logger.class);
        Agent agent = mock(Agent.class);
        when(agent.getAgentId()).thenReturn("chat");
        Model model = mock(Model.class);
        when(model.getModelName()).thenReturn("mock-chat");
        ModelCallInput input = new ModelCallInput(List.of(), List.of(), null, model);

        new LoggingMiddleware(log)
                .onModelCall(agent, RuntimeContext.empty(), input, ignored -> Flux.empty())
                .blockLast();

        verify(log)
                .info(
                        "chat middleware onModelCall start agentId={} sessionId={} model={}",
                        "chat",
                        "-",
                        "mock-chat");
    }

    @Test
    void onActingLogsToolNames() {
        Logger log = mock(Logger.class);
        Agent agent = mock(Agent.class);
        when(agent.getAgentId()).thenReturn("chat");
        ActingInput input = new ActingInput(List.of(ToolUseBlock.builder()
                .id("c1")
                .name("calculate")
                .input(Map.of())
                .build()));

        new LoggingMiddleware(log)
                .onActing(agent, RuntimeContext.empty(), input, ignored -> Flux.empty())
                .blockLast();

        verify(log)
                .info(
                        "chat middleware onActing start agentId={} sessionId={} tools={}",
                        "chat",
                        "-",
                        "calculate");
    }

    @Test
    void onAgentErrorStillLogsElapsed() {
        Logger log = mock(Logger.class);
        Agent agent = mock(Agent.class);
        when(agent.getAgentId()).thenReturn("chat");

        try {
            new LoggingMiddleware(log)
                    .onAgent(
                            agent,
                            RuntimeContext.empty(),
                            new AgentInput(List.of()),
                            ignored -> Flux.error(new RuntimeException("boom")))
                    .blockLast();
        } catch (RuntimeException ignored) {
            // 传播给调用方；日志已打
        }

        verify(log)
                .warn(
                        eq("chat middleware onAgent error agentId={} sessionId={} elapsedMs={} message={}"),
                        eq("chat"),
                        eq("-"),
                        anyLong(),
                        eq("boom"));
    }
}
