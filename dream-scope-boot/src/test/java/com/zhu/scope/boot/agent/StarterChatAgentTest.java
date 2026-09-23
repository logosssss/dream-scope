package com.zhu.scope.boot.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.scope.boot.agent.bean.request.StarterRequest;
import com.zhu.scope.boot.agent.bean.response.StarterResult;
import com.zhu.scope.boot.agent.event.StarterEvent;
import com.zhu.scope.boot.agent.excption.StarterProviderException;
import com.zhu.scope.boot.agent.excption.StarterTimeoutException;
import com.zhu.scope.boot.agent.impl.StarterChatAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class StarterChatAgentTest {

    @Test
    void handleReturnsModelText() {
        HarnessAgent react = mock(HarnessAgent.class);
        when(react.call(anyList(), any(RuntimeContext.class)))
                .thenReturn(Mono.just(new UserMessage("hello")));
        StarterChatAgent agent = new StarterChatAgent(react);

        StarterResult result = agent.handle(new StarterRequest(null, null, null, "hi"));

        assertEquals(StarterHandler.CHAT, result.agentId());
        assertEquals("hello", result.output());
    }

    @Test
    void blockingTimeoutMapsToAgentTimeout() {
        HarnessAgent react = mock(HarnessAgent.class);
        when(react.call(anyList(), any(RuntimeContext.class)))
                .thenReturn(Mono.error(new TimeoutException("late")));
        StarterChatAgent agent = new StarterChatAgent(react, Duration.ofSeconds(1));

        assertThrows(
                StarterTimeoutException.class,
                () -> agent.handle(new StarterRequest(null, null, null, "hi")));
    }

    @Test
    void otherFailureMapsToProvider() {
        HarnessAgent react = mock(HarnessAgent.class);
        when(react.call(anyList(), any(RuntimeContext.class)))
                .thenReturn(Mono.error(new IllegalStateException("upstream")));
        StarterChatAgent agent = new StarterChatAgent(react);

        assertThrows(
                StarterProviderException.class,
                () -> agent.handle(new StarterRequest(null, null, null, "hi")));
    }

    @Test
    void streamEmptyCompletes() {
        HarnessAgent react = mock(HarnessAgent.class);
        when(react.streamEvents(anyList(), any(RuntimeContext.class))).thenReturn(Flux.empty());
        StarterChatAgent agent = new StarterChatAgent(react);
        RecordingHandler handler = new RecordingHandler();

        agent.streamHandle(new StarterRequest(null, null, null, "hi"), handler);

        assertEquals(1, handler.completed);
        assertEquals(0, handler.errors);
    }

    private static final class RecordingHandler implements StarterHandler.Sink {
        int completed;
        int errors;

        @Override
        public void onEvent(StarterEvent event) {}

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
