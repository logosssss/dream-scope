package com.zhu.scope.adapter.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.Model;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * chat 可观测中间件：在 {@code onAgent} / {@code onModelCall} / {@code onActing} 打结构化 INFO。
 * 不依赖 Spring。2.0.3 的 {@link MiddlewareBase} 是接口，不是抽象类。
 */
public final class LoggingMiddleware implements MiddlewareBase {

    private final Logger log;

    public LoggingMiddleware() {
        this(LoggerFactory.getLogger(LoggingMiddleware.class));
    }

    LoggingMiddleware(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        String agentId = agentId(agent);
        String sessionId = sessionId(ctx);
        long start = System.nanoTime();
        log.info("chat middleware onAgent start agentId={} sessionId={}", agentId, sessionId);
        return next.apply(input)
                .doOnComplete(() -> log.info(
                        "chat middleware onAgent complete agentId={} sessionId={} elapsedMs={}",
                        agentId,
                        sessionId,
                        elapsedMs(start)))
                .doOnError(error -> log.warn(
                        "chat middleware onAgent error agentId={} sessionId={} elapsedMs={} message={}",
                        agentId,
                        sessionId,
                        elapsedMs(start),
                        error == null ? "" : error.getMessage()));
    }

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent,
            RuntimeContext ctx,
            ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        String agentId = agentId(agent);
        String sessionId = sessionId(ctx);
        String model = modelName(input);
        long start = System.nanoTime();
        log.info(
                "chat middleware onModelCall start agentId={} sessionId={} model={}",
                agentId,
                sessionId,
                model);
        return next.apply(input)
                .doOnComplete(() -> log.info(
                        "chat middleware onModelCall complete agentId={} sessionId={} model={} elapsedMs={}",
                        agentId,
                        sessionId,
                        model,
                        elapsedMs(start)))
                .doOnError(error -> log.warn(
                        "chat middleware onModelCall error agentId={} sessionId={} model={} elapsedMs={} message={}",
                        agentId,
                        sessionId,
                        model,
                        elapsedMs(start),
                        error == null ? "" : error.getMessage()));
    }

    @Override
    public Flux<AgentEvent> onActing(
            Agent agent,
            RuntimeContext ctx,
            ActingInput input,
            Function<ActingInput, Flux<AgentEvent>> next) {
        String agentId = agentId(agent);
        String sessionId = sessionId(ctx);
        String tools = toolNames(input);
        long start = System.nanoTime();
        log.info(
                "chat middleware onActing start agentId={} sessionId={} tools={}",
                agentId,
                sessionId,
                tools);
        return next.apply(input)
                .doOnComplete(() -> log.info(
                        "chat middleware onActing complete agentId={} sessionId={} tools={} elapsedMs={}",
                        agentId,
                        sessionId,
                        tools,
                        elapsedMs(start)))
                .doOnError(error -> log.warn(
                        "chat middleware onActing error agentId={} sessionId={} tools={} elapsedMs={} message={}",
                        agentId,
                        sessionId,
                        tools,
                        elapsedMs(start),
                        error == null ? "" : error.getMessage()));
    }

    private static String agentId(Agent agent) {
        if (agent == null) {
            return "-";
        }
        String id = agent.getAgentId();
        if (id == null || id.isBlank()) {
            id = agent.getName();
        }
        return id == null || id.isBlank() ? "-" : id;
    }

    private static String sessionId(RuntimeContext ctx) {
        if (ctx == null || ctx.getSessionId() == null || ctx.getSessionId().isBlank()) {
            return "-";
        }
        return ctx.getSessionId();
    }

    private static String modelName(ModelCallInput input) {
        if (input == null) {
            return "-";
        }
        Model model = input.model();
        if (model == null || model.getModelName() == null || model.getModelName().isBlank()) {
            return "-";
        }
        return model.getModelName();
    }

    private static String toolNames(ActingInput input) {
        if (input == null || input.toolCalls() == null || input.toolCalls().isEmpty()) {
            return "-";
        }
        return input.toolCalls().stream()
                .map(ToolUseBlock::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(","));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
