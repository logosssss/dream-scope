package com.zhu.scope.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentProviderException;
import com.zhu.scope.agent.AgentTimeoutException;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class StarterChatAgentTest {

    @Test
    void handleReturnsModelText() {
        ReActAgent react = mock(ReActAgent.class);
        when(react.call(anyList(), any(RuntimeContext.class)))
                .thenReturn(Mono.just(new UserMessage("hello")));
        StarterChatAgent agent = new StarterChatAgent(react);

        AgentInvokeResult result = agent.handle(new AgentInvokeRequest(null, null, null, "hi"));

        assertEquals(AgentIds.CHAT, result.agentId());
        assertEquals("hello", result.output());
    }

    @Test
    void blockingTimeoutMapsToAgentTimeout() {
        ReActAgent react = mock(ReActAgent.class);
        when(react.call(anyList(), any(RuntimeContext.class)))
                .thenReturn(Mono.error(new TimeoutException("late")));
        StarterChatAgent agent = new StarterChatAgent(react, Duration.ofSeconds(1));

        assertThrows(
                AgentTimeoutException.class,
                () -> agent.handle(new AgentInvokeRequest(null, null, null, "hi")));
    }

    @Test
    void otherFailureMapsToProvider() {
        ReActAgent react = mock(ReActAgent.class);
        when(react.call(anyList(), any(RuntimeContext.class)))
                .thenReturn(Mono.error(new IllegalStateException("upstream")));
        StarterChatAgent agent = new StarterChatAgent(react);

        assertThrows(
                AgentProviderException.class,
                () -> agent.handle(new AgentInvokeRequest(null, null, null, "hi")));
    }

    @Test
    void streamEmptyCompletes() {
        ReActAgent react = mock(ReActAgent.class);
        when(react.streamEvents(anyList(), any(RuntimeContext.class))).thenReturn(Flux.empty());
        StarterChatAgent agent = new StarterChatAgent(react);
        RecordingHandler handler = new RecordingHandler();

        agent.streamHandle(new AgentInvokeRequest(null, null, null, "hi"), handler);

        assertEquals(1, handler.completed);
        assertEquals(0, handler.errors);
    }

    private static final class RecordingHandler implements com.zhu.scope.agent.AgentStreamHandler {
        int completed;
        int errors;

        @Override
        public void onEvent(com.zhu.scope.agent.AgentEvent event) {}

        @Override
        public void onComplete() {
            completed++;
        }

        @Override
        public void onError(Throwable error) {
            errors++;
        }
    }
}
