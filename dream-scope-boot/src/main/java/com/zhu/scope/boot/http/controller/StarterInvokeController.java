package com.zhu.scope.boot.http.controller;

import com.zhu.scope.boot.http.bean.request.StarterInvokeHttpRequest;

import com.zhu.scope.boot.agent.event.StarterEvent;
import com.zhu.scope.boot.agent.StarterHandler;
import com.zhu.scope.boot.agent.StarterHandlerRegistry;
import com.zhu.scope.boot.agent.bean.request.StarterRequest;
import com.zhu.scope.boot.agent.bean.response.StarterResult;
import com.zhu.scope.boot.config.BootScopeProperties;
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

/** boot 自己的 {@code /api/agents/invoke}、{@code /stream}。 */
@RestController
public class StarterInvokeController {

    private static final Duration SSE_TIMEOUT_BUFFER = Duration.ofSeconds(30);

    private final StarterHandlerRegistry registry;

    private final BootScopeProperties properties;

    public StarterInvokeController(StarterHandlerRegistry registry, BootScopeProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @PostMapping("/api/agents/invoke")
    public StarterResult invoke(@RequestBody StarterInvokeHttpRequest body) {
        StarterRequest request = toRequest(body);
        if (!request.hasInput()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input required");
        }
        return requireHandler(request.agentId()).handle(request);
    }

    @PostMapping(value = "/api/agents/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody StarterInvokeHttpRequest body) {
        StarterRequest request = toRequest(body);
        if (!request.hasInput()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input required");
        }
        StarterHandler handler = requireHandler(request.agentId());
        Duration sseTimeout = properties.getChatTimeout().plus(SSE_TIMEOUT_BUFFER);
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        SseBridge bridge = new SseBridge(emitter);
        emitter.onTimeout(bridge::cancel);
        emitter.onCompletion(bridge::cancel);
        emitter.onError(error -> bridge.cancel());
        handler.streamHandle(request, bridge);
        return emitter;
    }

    private StarterHandler requireHandler(String agentId) {
        return registry
                .find(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown agent: " + agentId));
    }

    private static StarterRequest toRequest(StarterInvokeHttpRequest body) {
        try {
            if (body == null) {
                return new StarterRequest(null, null, null, "");
            }
            return new StarterRequest(
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

    static final class SseBridge implements StarterHandler.Sink {

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
        public void onEvent(StarterEvent event) {
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
                StarterEvent.Error payload =
                        new StarterEvent.Error(error == null ? "" : String.valueOf(error.getMessage()));
                emitter.send(SseEmitter.event().name("error").data(bodyOf(payload), MediaType.APPLICATION_JSON));
            } catch (IOException ignored) {
                // 客户端已断开
            }
            emitter.completeWithError(error);
        }
    }

    static String typeOf(StarterEvent event) {
        return switch (event) {
            case StarterEvent.TextDelta ignored -> "textDelta";
            case StarterEvent.ToolCall ignored -> "toolCall";
            case StarterEvent.ToolResult ignored -> "toolResult";
            case StarterEvent.Hint ignored -> "hint";
            case StarterEvent.Done ignored -> "done";
            case StarterEvent.Error ignored -> "error";
        };
    }

    static Map<String, Object> bodyOf(StarterEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", typeOf(event));
        switch (event) {
            case StarterEvent.TextDelta delta -> body.put("text", nullToEmpty(delta.text()));
            case StarterEvent.ToolCall call -> {
                body.put("toolName", nullToEmpty(call.toolName()));
                body.put("input", nullToEmpty(call.input()));
            }
            case StarterEvent.ToolResult result -> {
                body.put("toolName", nullToEmpty(result.toolName()));
                body.put("output", nullToEmpty(result.output()));
            }
            case StarterEvent.Hint hint -> body.put("text", nullToEmpty(hint.text()));
            case StarterEvent.Done done -> {
                body.put("finalOutput", nullToEmpty(done.finalOutput()));
                body.put("inputTokens", done.inputTokens());
                body.put("outputTokens", done.outputTokens());
                body.put("planActive", done.planActive());
                if (done.data() != null) {
                    body.put("data", done.data());
                }
            }
            case StarterEvent.Error error -> body.put("message", nullToEmpty(error.message()));
        }
        return body;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
