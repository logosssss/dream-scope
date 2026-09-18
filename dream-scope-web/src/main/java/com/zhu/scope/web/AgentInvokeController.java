package com.zhu.scope.web;

import com.zhu.scope.adapter.event.StreamCancelHook;
import com.zhu.scope.agent.AgentEvent;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentRegistry;
import com.zhu.scope.agent.AgentStreamHandler;
import com.zhu.scope.agent.StreamingAgentHandler;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 同步 {@code /invoke} 与 SSE {@code /stream}。只认 domain 类型。
 */
@RestController
public class AgentInvokeController {

    private static final Duration SSE_TIMEOUT_BUFFER = Duration.ofSeconds(30);

    private final AgentRegistry registry;

    private final DreamScopeProperties properties;

    public AgentInvokeController(AgentRegistry registry, DreamScopeProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @PostMapping("/api/agents/invoke")
    public AgentInvokeHttpResponse invoke(@RequestBody AgentInvokeHttpRequest body) {
        AgentInvokeRequest request = toDomain(body);
        if (!request.hasInput()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input required");
        }
        AgentHandler handler = requireHandler(request.agentId());
        AgentInvokeResult result = handler.handle(request);
        return new AgentInvokeHttpResponse(
                result.agentId(),
                result.output(),
                result.inputTokens(),
                result.outputTokens(),
                result.data(),
                result.planActive());
    }

    @PostMapping(value = "/api/agents/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AgentInvokeHttpRequest body) {
        AgentInvokeRequest request = toDomain(body);
        if (!request.hasInput()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input required");
        }
        AgentHandler handler = requireHandler(request.agentId());
        if (!(handler instanceof StreamingAgentHandler streaming)) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "streaming not supported: " + request.agentId());
        }
        Duration sseTimeout = properties.getChatTimeout().plus(SSE_TIMEOUT_BUFFER);
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        SseBridge bridge = new SseBridge(emitter);
        emitter.onTimeout(bridge::cancel);
        emitter.onCompletion(bridge::cancel);
        emitter.onError(error -> bridge.cancel());
        streaming.streamHandle(request, bridge);
        return emitter;
    }

    private AgentHandler requireHandler(String agentId) {
        return registry
                .find(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown agent: " + agentId));
    }

    private static AgentInvokeRequest toDomain(AgentInvokeHttpRequest body) {
        try {
            if (body == null) {
                return new AgentInvokeRequest(null, null, null, "");
            }
            return new AgentInvokeRequest(
                    body.agentId(),
                    body.sessionId(),
                    body.userId(),
                    body.input(),
                    body.imageUrls(),
                    Boolean.TRUE.equals(body.structured()),
                    body.jsonSchema());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    static final class SseBridge implements AgentStreamHandler, StreamCancelHook {

        private final SseEmitter emitter;

        private final AtomicBoolean finished = new AtomicBoolean();

        private volatile Runnable cancel = () -> {};

        SseBridge(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void bindCancel(Runnable cancel) {
            this.cancel = cancel == null ? () -> {} : cancel;
        }

        void cancel() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            cancel.run();
            emitter.complete();
        }

        @Override
        public void onEvent(AgentEvent event) {
            if (finished.get()) {
                return;
            }
            try {
                String type = typeOf(event);
                emitter.send(SseEmitter.event().name(type).data(bodyOf(event), MediaType.APPLICATION_JSON));
            } catch (IOException ex) {
                cancel();
            }
        }

        @Override
        public void onComplete() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            emitter.complete();
        }

        @Override
        public void onError(Throwable error) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            try {
                AgentEvent.Error payload =
                        new AgentEvent.Error(error == null ? "" : String.valueOf(error.getMessage()));
                emitter.send(SseEmitter.event().name("error").data(bodyOf(payload), MediaType.APPLICATION_JSON));
            } catch (IOException ignored) {
                // 客户端已断开
            }
            emitter.completeWithError(error);
        }
    }

    static String typeOf(AgentEvent event) {
        return switch (event) {
            case AgentEvent.TextDelta ignored -> "textDelta";
            case AgentEvent.ToolCall ignored -> "toolCall";
            case AgentEvent.ToolResult ignored -> "toolResult";
            case AgentEvent.Hint ignored -> "hint";
            case AgentEvent.Done ignored -> "done";
            case AgentEvent.Error ignored -> "error";
        };
    }

    static Map<String, Object> bodyOf(AgentEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", typeOf(event));
        switch (event) {
            case AgentEvent.TextDelta delta -> body.put("text", nullToEmpty(delta.text()));
            case AgentEvent.ToolCall call -> {
                body.put("toolName", nullToEmpty(call.toolName()));
                body.put("input", nullToEmpty(call.input()));
            }
            case AgentEvent.ToolResult result -> {
                body.put("toolName", nullToEmpty(result.toolName()));
                body.put("output", nullToEmpty(result.output()));
            }
            case AgentEvent.Hint hint -> body.put("text", nullToEmpty(hint.text()));
            case AgentEvent.Done done -> {
                body.put("finalOutput", nullToEmpty(done.finalOutput()));
                body.put("inputTokens", done.inputTokens());
                body.put("outputTokens", done.outputTokens());
                body.put("planActive", done.planActive());
                if (done.data() != null) {
                    body.put("data", done.data());
                }
            }
            case AgentEvent.Error error -> body.put("message", nullToEmpty(error.message()));
        }
        return body;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
