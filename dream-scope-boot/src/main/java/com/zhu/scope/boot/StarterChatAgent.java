package com.zhu.scope.boot;

import com.zhu.scope.adapter.MessageCodec;
import com.zhu.scope.adapter.event.EventCodec;
import com.zhu.scope.adapter.event.StreamCancelHook;
import com.zhu.scope.agent.AgentEvent;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentProviderException;
import com.zhu.scope.agent.AgentStreamHandler;
import com.zhu.scope.agent.AgentTimeoutException;
import com.zhu.scope.agent.StreamingAgentHandler;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

/**
 * 把官方 starter 的 {@link ReActAgent} 收成 domain {@link StreamingAgentHandler}。
 *
 * <p>没有 Harness / Redis / Plan Mode / Skill；对照路径只验证自动配置能跑通同一套 HTTP。
 */
public final class StarterChatAgent implements StreamingAgentHandler {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final ReActAgent agent;

    private final Duration callTimeout;

    public StarterChatAgent(ReActAgent agent) {
        this(agent, DEFAULT_TIMEOUT);
    }

    public StarterChatAgent(ReActAgent agent, Duration callTimeout) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.callTimeout = normalizeTimeout(callTimeout);
    }

    @Override
    public String id() {
        return AgentIds.CHAT;
    }

    @Override
    public AgentInvokeResult handle(AgentInvokeRequest request) {
        Msg inbound = MessageCodec.toUserMessage(request.input(), request.imageUrls());
        RuntimeContext context = contextOf(request);
        try {
            Msg outbound = callMono(inbound, context, request).block(callTimeout);
            return new AgentInvokeResult(
                    id(),
                    MessageCodec.textOf(outbound),
                    0,
                    0,
                    MessageCodec.structuredOf(outbound),
                    false);
        } catch (RuntimeException ex) {
            throw mapError(ex);
        }
    }

    @Override
    public void streamHandle(AgentInvokeRequest request, AgentStreamHandler handler) {
        Objects.requireNonNull(handler, "handler");
        Msg inbound = MessageCodec.toUserMessage(request.input(), request.imageUrls());
        RuntimeContext context = contextOf(request);
        EventCodec codec = new EventCodec();
        Disposable disposable = agent.streamEvents(List.of(inbound), context)
                .timeout(callTimeout)
                .subscribe(
                        event -> codec.toDomain(event).ifPresent(handler::onEvent),
                        error -> handler.onError(mapError(error)),
                        handler::onComplete);
        if (handler instanceof StreamCancelHook hook) {
            hook.bindCancel(() -> {
                disposable.dispose();
                try {
                    agent.interrupt(context);
                } catch (RuntimeException ignored) {
                    // 订阅已取消即可
                }
            });
        }
    }

    private Mono<Msg> callMono(Msg inbound, RuntimeContext context, AgentInvokeRequest request) {
        List<Msg> messages = List.of(inbound);
        if (!request.structured()) {
            return agent.call(messages, context);
        }
        JsonNode schema = MessageCodec.jsonSchemaNode(request.jsonSchema());
        if (schema != null) {
            return agent.call(messages, schema, context);
        }
        return agent.call(messages, Map.class, context);
    }

    private RuntimeException mapError(Throwable error) {
        if (error instanceof AgentTimeoutException timeout) {
            return timeout;
        }
        if (error instanceof AgentProviderException provider) {
            return provider;
        }
        if (isTimeout(error)) {
            return new AgentTimeoutException("chat timed out after " + callTimeout, error);
        }
        return new AgentProviderException("chat model call failed", error);
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (current instanceof IllegalStateException
                    && message != null
                    && message.startsWith("Timeout on blocking read")) {
                return true;
            }
        }
        return false;
    }

    private static RuntimeContext contextOf(AgentInvokeRequest request) {
        return RuntimeContext.builder()
                .userId(blankToNull(request.userId()))
                .sessionId(blankToNull(request.sessionId()))
                .build();
    }

    private static Duration normalizeTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return DEFAULT_TIMEOUT;
        }
        return timeout;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
