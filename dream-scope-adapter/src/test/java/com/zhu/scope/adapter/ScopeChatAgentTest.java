package com.zhu.scope.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.scope.adapter.middleware.LoggingMiddleware;
import com.zhu.scope.adapter.subagent.ChatSubagents;
import com.zhu.scope.agent.AgentEvent;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentProviderException;
import com.zhu.scope.agent.AgentStreamHandler;
import com.zhu.scope.agent.AgentTimeoutException;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class ScopeChatAgentTest {

    @Test
    void handleReturnsMockedChatModelReply() {
        try (ScopeChatAgent agent = new ScopeChatAgent(textModel("mocked-reply"))) {
            AgentInvokeResult result =
                    agent.handle(new AgentInvokeRequest(AgentIds.CHAT, "s1", "u1", "你好"));

            assertEquals(AgentIds.CHAT, agent.id());
            assertEquals(AgentIds.CHAT, result.agentId());
            assertEquals("mocked-reply", result.output());
        }
    }

    @Test
    void constructAndCloseWithCompactionAndMemory() {
        try (ScopeChatAgent agent = new ScopeChatAgent(textModel("ok"))) {
            assertEquals(AgentIds.CHAT, agent.id());
        }
    }

    @Test
    void chatMiddlewaresRegistersLoggingMiddleware() {
        assertEquals(1, ScopeChatAgent.chatMiddlewares().size());
        assertInstanceOf(LoggingMiddleware.class, ScopeChatAgent.chatMiddlewares().getFirst());
    }

    @Test
    void chatSubagentsRegistersProgrammaticSummarizer() {
        assertEquals(1, ScopeChatAgent.chatSubagents().size());
        assertEquals(ChatSubagents.SUMMARIZER_ID, ScopeChatAgent.chatSubagents().getFirst().getName());
    }

    @Test
    void compactionConfigUsesMessageCountKeepMode() {
        CompactionConfig config = ScopeChatAgent.compactionConfig(30, 10);
        assertEquals(30, config.getTriggerMessages());
        assertEquals(10, config.getKeepMessages());
        assertEquals(0, config.getKeepTokens());
    }

    @Test
    void generateOptionsSkippedWhenUnset() {
        ChatHarnessOptions options =
                new ChatHarnessOptions(
                        Duration.ofSeconds(1), null, 0, 0, null, null, null, null, null, null, null, null, null);
        assertEquals(null, ScopeChatAgent.generateOptions(options));
    }

    @Test
    void generateOptionsMapsConfiguredFields() {
        ChatHarnessOptions options =
                new ChatHarnessOptions(
                        Duration.ofSeconds(1), null, 0, 0, null, null, 0.2, 0.8, 1024, null, null, null, null);
        GenerateOptions mapped = ScopeChatAgent.generateOptions(options);
        assertEquals(0.2, mapped.getTemperature());
        assertEquals(0.8, mapped.getTopP());
        assertEquals(1024, mapped.getMaxTokens());
    }

    @Test
    void resolveFallbackModelSkipsBlankOrSameId() {
        ChatHarnessOptions blank =
                new ChatHarnessOptions(
                        Duration.ofSeconds(1), null, 0, 0, null, null, null, null, null, "  ", null, null, null);
        assertEquals(null, ScopeChatAgent.resolveFallbackModel("dashscope:qwen-plus", blank));
        ChatHarnessOptions same =
                new ChatHarnessOptions(
                        Duration.ofSeconds(1),
                        null,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "dashscope:qwen-plus",
                        "key",
                        null,
                        null);
        assertEquals(null, ScopeChatAgent.resolveFallbackModel("dashscope:qwen-plus", same));
        ChatHarnessOptions missingKey =
                new ChatHarnessOptions(
                        Duration.ofSeconds(1),
                        null,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "dashscope:qwen-turbo",
                        "  ",
                        null,
                        null);
        assertThrows(
                IllegalStateException.class,
                () -> ScopeChatAgent.resolveFallbackModel("dashscope:qwen-plus", missingKey));
    }

    @Test
    void resolveModelIdPrefersChatThenDefault() {
        assertEquals("dashscope:qwen-max", ScopeChatAgent.resolveModelId("dashscope:qwen-max", "ignored"));
        assertEquals("dashscope:qwen-plus", ScopeChatAgent.resolveModelId("  ", "dashscope:qwen-plus"));
        assertEquals(ScopeChatAgent.DEFAULT_MODEL_ID, ScopeChatAgent.resolveModelId(null, null));
    }

    @Test
    void apiKeyPropertyFollowsProviderPrefix() {
        assertEquals("DASHSCOPE_API_KEY", ScopeChatAgent.apiKeyProperty("dashscope:qwen-plus"));
        assertEquals("DEEPSEEK_API_KEY", ScopeChatAgent.apiKeyProperty("deepseek:deepseek-chat"));
        assertEquals("OPENAI_API_KEY", ScopeChatAgent.apiKeyProperty("openai:gpt-4.1-mini"));
    }

    @Test
    void streamHandleEmitsTextThenDone() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        try (ScopeChatAgent agent = new ScopeChatAgent(textModel("mocked-reply"))) {
            agent.streamHandle(new AgentInvokeRequest(AgentIds.CHAT, "s1", "u1", "你好"), handler);
            assertTrue(handler.await(5, TimeUnit.SECONDS));
        }
        assertEquals(null, handler.error);
        assertTrue(handler.completed);
        String output = handler.output();
        assertEquals("mocked-reply", output);
        assertTrue(handler.hasTextOrDone());
    }

    @Test
    void handleTimeoutBecomesAgentTimeoutException() {
        Model chatModel = mock(Model.class);
        when(chatModel.getModelName()).thenReturn("mock-chat");
        when(chatModel.stream(any(), any(), any())).thenReturn(Flux.never());

        try (ScopeChatAgent agent = new ScopeChatAgent(chatModel, Duration.ofMillis(200), null)) {
            assertThrows(
                    AgentTimeoutException.class,
                    () -> agent.handle(new AgentInvokeRequest(AgentIds.CHAT, "s", "u", "你好")));
        }
    }

    @Test
    void handleModelErrorBecomesAgentProviderException() {
        Model chatModel = mock(Model.class);
        when(chatModel.getModelName()).thenReturn("mock-chat");
        when(chatModel.stream(any(), any(), any())).thenReturn(Flux.error(new RuntimeException("upstream")));

        try (ScopeChatAgent agent = new ScopeChatAgent(chatModel)) {
            assertThrows(
                    AgentProviderException.class,
                    () -> agent.handle(new AgentInvokeRequest(AgentIds.CHAT, "s", "u", "你好")));
        }
    }

    private static Model textModel(String text) {
        Model chatModel = mock(Model.class);
        when(chatModel.getModelName()).thenReturn("mock-chat");
        when(chatModel.stream(any(), any(), any()))
                .thenReturn(Flux.just(ChatResponse.builder()
                        .content(List.of(TextBlock.builder().text(text).build()))
                        .usage(new ChatUsage(1, 1, 0))
                        .build()));
        return chatModel;
    }

    private static final class RecordingHandler implements AgentStreamHandler {
        private final List<AgentEvent> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Throwable error;
        private volatile boolean completed;

        @Override
        public void onEvent(AgentEvent event) {
            events.add(event);
        }

        @Override
        public void onComplete() {
            completed = true;
            latch.countDown();
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
            latch.countDown();
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        boolean hasTextOrDone() {
            return events.stream().anyMatch(e -> e instanceof AgentEvent.TextDelta || e instanceof AgentEvent.Done);
        }

        String output() {
            String lastDone = "";
            StringBuilder deltas = new StringBuilder();
            for (AgentEvent event : events) {
                if (event instanceof AgentEvent.TextDelta delta && delta.text() != null) {
                    deltas.append(delta.text());
                }
                if (event instanceof AgentEvent.Done done && done.finalOutput() != null) {
                    lastDone = done.finalOutput();
                }
            }
            return lastDone.isEmpty() ? deltas.toString() : lastDone;
        }
    }
}
