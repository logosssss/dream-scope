package com.zhu.scope.boot.agent.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhu.scope.boot.agent.event.StarterEventCodec;
import com.zhu.scope.boot.agent.StarterHandler;
import com.zhu.scope.boot.agent.message.StarterMessages;
import com.zhu.scope.boot.agent.bean.request.StarterRequest;
import com.zhu.scope.boot.agent.bean.response.StarterResult;
import com.zhu.scope.boot.agent.excption.StarterProviderException;
import com.zhu.scope.boot.agent.excption.StarterTimeoutException;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

/**
 * boot 的调用入口。模型来自 starter，计划走 {@link HarnessAgent}。
 * 文件工具和 shell 关闭，权限为直接放行，避免 plan_exit 停住会话。
 */
public final class StarterChatAgent implements StarterHandler {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final HarnessAgent agent;

    private final Duration callTimeout;

    public StarterChatAgent(HarnessAgent agent) {
        this(agent, DEFAULT_TIMEOUT);
    }

    public StarterChatAgent(HarnessAgent agent, Duration callTimeout) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.callTimeout = normalizeTimeout(callTimeout);
    }

    @Override
    public String id() {
        return CHAT;
    }

    @Override
    public StarterResult handle(StarterRequest request) {
        Msg inbound = StarterMessages.toUserMessage(request.input(), request.imageUrls());
        RuntimeContext context = contextOf(request);
        try {
            Msg outbound = callMono(inbound, context, request).block(callTimeout);
            return new StarterResult(
                    id(),
                    StarterMessages.textOf(outbound),
                    0,
                    0,
                    StarterMessages.structuredOf(outbound),
                    false);
        } catch (RuntimeException ex) {
            throw mapError(ex);
        }
    }

    @Override
    public void streamHandle(StarterRequest request, Sink sink) {
        Objects.requireNonNull(sink, "sink");
        Msg inbound = StarterMessages.toUserMessage(request.input(), request.imageUrls());
        RuntimeContext context = contextOf(request);
        StarterEventCodec codec = new StarterEventCodec();
        Disposable disposable = agent.streamEvents(List.of(inbound), context)
                .timeout(callTimeout)
                .subscribe(
                        event -> codec.toEvent(event).ifPresent(sink::onEvent),
                        error -> sink.onError(mapError(error)),
                        sink::onComplete);
        sink.bindCancel(() -> {
            disposable.dispose();
            try {
                agent.interrupt(context);
            } catch (RuntimeException ignored) {
                // 订阅已取消即可
            }
        });
    }

    private Mono<Msg> callMono(Msg inbound, RuntimeContext context, StarterRequest request) {
        List<Msg> messages = List.of(inbound);
        if (!request.structured()) {
            return agent.call(messages, context);
        }
        JsonNode schema = StarterMessages.jsonSchemaNode(request.jsonSchema());
        if (schema != null) {
            return agent.call(messages, schema, context);
        }
        return agent.call(messages, Map.class, context);
    }

    private RuntimeException mapError(Throwable error) {
        if (error instanceof StarterTimeoutException timeout) {
            return timeout;
        }
        if (error instanceof StarterProviderException provider) {
            return provider;
        }
        if (isTimeout(error)) {
            return new StarterTimeoutException("chat timed out after " + callTimeout, error);
        }
        return new StarterProviderException("chat model call failed", error);
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

    private static RuntimeContext contextOf(StarterRequest request) {
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
