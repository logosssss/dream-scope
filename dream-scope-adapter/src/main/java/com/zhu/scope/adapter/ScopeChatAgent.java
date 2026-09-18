package com.zhu.scope.adapter;

import com.zhu.scope.adapter.event.EventCodec;
import com.zhu.scope.adapter.event.StreamCancelHook;
import com.zhu.scope.adapter.middleware.LoggingMiddleware;
import com.zhu.scope.adapter.subagent.ChatSubagents;
import com.zhu.scope.adapter.tool.ChatTools;
import com.zhu.scope.agent.AgentEvent;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentProviderException;
import com.zhu.scope.agent.AgentStreamHandler;
import com.zhu.scope.agent.AgentTimeoutException;
import com.zhu.scope.agent.StreamingAgentHandler;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.tool.AgentSpawnTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import redis.clients.jedis.JedisPooled;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * 内置 {@code chat} Agent：对外 {@link StreamingAgentHandler}，对内 {@link HarnessAgent}。
 *
 * <p>不依赖 Spring。模型 / Key / 超时 / 工作区 / 压缩阈值 / Redis 由 web 组合根注入 {@link #create}。
 * 生产用 {@code RedisDistributedStore}（必选）。HTTP 进程关掉 Harness 默认的工作区文件工具和 Shell，只保留 {@link ChatTools} 与 {@code agent_spawn}。
 * Middleware 挂点在 {@link #chatMiddlewares()}。
 */
public final class ScopeChatAgent implements StreamingAgentHandler, AutoCloseable {

    static final String DEFAULT_MODEL_ID = "dashscope:qwen-plus";

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private static final String SYS_PROMPT =
            "你是一个有帮助的助手。需要当前时间、四则运算或抓取网页时，调用 getCurrentTime / calculate / httpGet。"
                    + "当用户询问天气时，调用 agent_spawn，agent_id 为 weather-agent，task 写明城市与日期。"
                    + "当用户询问航班时，调用 agent_spawn，agent_id 为 flight-agent，task 写明出发地、目的地与日期。"
                    + "当用户要求总结、摘要或缩写一段话时，调用 agent_spawn，agent_id 为 summarizer，task 为待处理原文。"
                    + "子 Agent 返回的是演示结果，转述给用户即可；不要自己编造天气、航班或摘要。"
                    + "当用户要求写会议纪要、讨论要点或待办清单时，先查看 available_skills，调用 load_skill_through_path 加载 meeting-notes 的 SKILL.md，再按其中步骤整理。";

    private static final String PLAN_PROMPT =
            "复杂任务可先调用 plan_enter，用 plan_write 写下步骤，确认后再 plan_exit 执行。普通问答不必进入计划模式。";

    private final HarnessAgent agent;

    private final Duration callTimeout;

    private final AutoCloseable redisClient;

    private final boolean planModeEnabled;

    public ScopeChatAgent(Model model) {
        this(model, DEFAULT_TIMEOUT, null, tempWorkspace());
    }

    public ScopeChatAgent(Model model, Duration callTimeout, AgentStateStore stateStore) {
        this(model, callTimeout, stateStore, tempWorkspace());
    }

    public ScopeChatAgent(Model model, Duration callTimeout, AgentStateStore stateStore, Path workspace) {
        this(
                model,
                callTimeout,
                stateStore,
                workspace,
                ChatHarnessOptions.DEFAULT_TRIGGER_MESSAGES,
                ChatHarnessOptions.DEFAULT_KEEP_MESSAGES);
    }

    public ScopeChatAgent(
            Model model,
            Duration callTimeout,
            AgentStateStore stateStore,
            Path workspace,
            int compactionTriggerMessages,
            int compactionKeepMessages) {
        this(
                model,
                callTimeout,
                stateStore,
                workspace,
                compactionTriggerMessages,
                compactionKeepMessages,
                null,
                null,
                null,
                null,
                false,
                "plans");
    }

    private ScopeChatAgent(
            Model model,
            Duration callTimeout,
            AgentStateStore stateStore,
            Path workspace,
            int compactionTriggerMessages,
            int compactionKeepMessages,
            GenerateOptions generateOptions,
            Model fallbackModel,
            DistributedStore distributedStore,
            AutoCloseable redisClient,
            boolean planModeEnabled,
            String planDirectory) {
        this.callTimeout = normalizeTimeout(callTimeout);
        this.redisClient = redisClient;
        this.planModeEnabled = planModeEnabled;
        String prompt = planModeEnabled ? SYS_PROMPT + PLAN_PROMPT : SYS_PROMPT;
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(AgentIds.CHAT)
                .sysPrompt(prompt)
                .model(Objects.requireNonNull(model, "model"))
                .toolkit(chatToolkit())
                .compaction(compactionConfig(compactionTriggerMessages, compactionKeepMessages))
                .memory(MemoryConfig.defaults())
                .subagents(chatSubagents())
                .middlewares(chatMiddlewares())
                .disableFilesystemTools()
                .disableShellTool();
        if (generateOptions != null) {
            builder.generateOptions(generateOptions);
        }
        if (fallbackModel != null) {
            builder.fallbackModel(fallbackModel);
        }
        if (distributedStore != null) {
            builder.distributedStore(distributedStore);
        } else if (stateStore != null) {
            builder.stateStore(stateStore);
        }
        boolean hasWorkspace = workspace != null && !workspace.toString().isBlank();
        if (hasWorkspace) {
            builder.workspace(workspace);
        }
        if (planModeEnabled && hasWorkspace) {
            builder.enablePlanMode()
                    .planFileDirectory(
                            planDirectory == null || planDirectory.isBlank() ? "plans" : planDirectory);
        }
        this.agent = builder.build();
    }

    /**
     * 生产装配：模型 + Redis DistributedStore + workspace + 压缩/记忆。
     */
    public static ScopeChatAgent create(String modelId, String apiKey, ChatHarnessOptions options) {
        Objects.requireNonNull(options, "options");
        JedisPooled jedis = ChatRedis.open(options.redisUri());
        try {
            String id = resolveModelId(modelId, null);
            Model model = resolveModel(id, apiKey);
            return new ScopeChatAgent(
                    model,
                    options.timeout(),
                    null,
                    blankPathToNull(options.workspace()),
                    options.compactionTriggerMessages(),
                    options.compactionKeepMessages(),
                    generateOptions(options),
                    resolveFallbackModel(id, options),
                    ChatRedis.store(jedis, options.redisKeyPrefix()),
                    jedis,
                    options.planModeEnabled(),
                    options.planDirectory());
        } catch (RuntimeException ex) {
            jedis.close();
            throw ex;
        }
    }

    static Model resolveModel(String modelId, String apiKey) {
        String id = resolveModelId(modelId, null);
        ModelCreationContext context = ModelCreationContext.builder().apiKey(apiKey).build();
        return ModelRegistry.resolve(id, context);
    }

    static Model resolveFallbackModel(String primaryId, ChatHarnessOptions options) {
        if (options == null || options.fallbackModelId() == null) {
            return null;
        }
        String fallbackId = resolveModelId(options.fallbackModelId(), null);
        if (fallbackId.equals(primaryId)) {
            return null;
        }
        String key = options.fallbackApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("fallback model api key required: " + fallbackId);
        }
        return resolveModel(fallbackId, key);
    }

    static GenerateOptions generateOptions(ChatHarnessOptions options) {
        if (options == null) {
            return null;
        }
        if (options.temperature() == null && options.topP() == null && options.maxTokens() == null) {
            return null;
        }
        GenerateOptions.Builder builder = GenerateOptions.builder();
        if (options.temperature() != null) {
            builder.temperature(options.temperature());
        }
        if (options.topP() != null) {
            builder.topP(options.topP());
        }
        if (options.maxTokens() != null) {
            builder.maxTokens(options.maxTokens());
        }
        return builder.build();
    }

    static CompactionConfig compactionConfig(int triggerMessages, int keepMessages) {
        int trigger = triggerMessages > 0 ? triggerMessages : ChatHarnessOptions.DEFAULT_TRIGGER_MESSAGES;
        int keep = keepMessages > 0 ? keepMessages : ChatHarnessOptions.DEFAULT_KEEP_MESSAGES;
        return CompactionConfig.builder()
                .triggerMessages(trigger)
                .keepMessages(keep)
                .keepTokens(0)
                .build();
    }

    static Toolkit chatToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ChatTools());
        return toolkit;
    }

    /**
     * 编程式子 Agent。Markdown 声明仍由 Harness 扫描 workspace {@code subagents/*.md}，两路合并。
     */
    static List<SubagentDeclaration> chatSubagents() {
        return ChatSubagents.programmatic();
    }

    /**
     * chat 横切挂点。当前挂 {@link LoggingMiddleware}；不要把 Middleware 类型漏到 web / domain。
     */
    static List<MiddlewareBase> chatMiddlewares() {
        return List.of(new LoggingMiddleware());
    }

    public static String resolveModelId(String chat, String fallback) {
        if (chat != null && !chat.isBlank()) {
            return chat.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return DEFAULT_MODEL_ID;
    }

    public static String apiKeyProperty(String modelId) {
        String id = modelId == null ? "" : modelId.trim().toLowerCase(Locale.ROOT);
        if (id.startsWith("deepseek:")) {
            return "DEEPSEEK_API_KEY";
        }
        if (id.startsWith("openai:")) {
            return "OPENAI_API_KEY";
        }
        if (id.startsWith("anthropic:")) {
            return "ANTHROPIC_API_KEY";
        }
        if (id.startsWith("gemini:")) {
            return "GEMINI_API_KEY";
        }
        return "DASHSCOPE_API_KEY";
    }

    @Override
    public String id() {
        return AgentIds.CHAT;
    }

    @Override
    public AgentInvokeResult handle(AgentInvokeRequest request) {
        CompletableFuture<AgentInvokeResult> done = new CompletableFuture<>();
        StringBuilder deltas = new StringBuilder();
        streamHandle(request, new AgentStreamHandler() {
            private volatile String lastDone = "";
            private volatile int inputTokens;
            private volatile int outputTokens;
            private volatile Map<String, Object> data;
            private volatile boolean planActive;

            @Override
            public void onEvent(AgentEvent event) {
                if (event instanceof AgentEvent.TextDelta delta && delta.text() != null) {
                    deltas.append(delta.text());
                }
                if (event instanceof AgentEvent.Done completed) {
                    if (completed.finalOutput() != null) {
                        lastDone = completed.finalOutput();
                    }
                    inputTokens = completed.inputTokens();
                    outputTokens = completed.outputTokens();
                    data = completed.data();
                    planActive = completed.planActive();
                }
            }

            @Override
            public void onComplete() {
                String text = lastDone.isEmpty() ? deltas.toString() : lastDone;
                done.complete(new AgentInvokeResult(id(), text, inputTokens, outputTokens, data, planActive));
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });
        try {
            return done.get(callTimeout.plusSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgentProviderException("chat model call interrupted", ex);
        } catch (TimeoutException ex) {
            throw new AgentTimeoutException("chat timed out after " + callTimeout, ex);
        } catch (ExecutionException ex) {
            throw mapStreamError(ex.getCause() == null ? ex : ex.getCause());
        }
    }

    @Override
    public void streamHandle(AgentInvokeRequest request, AgentStreamHandler handler) {
        Objects.requireNonNull(handler, "handler");
        Msg inbound = MessageCodec.toUserMessage(request.input(), request.imageUrls());
        RuntimeContext context = RuntimeContext.builder()
                .userId(blankToNull(request.userId()))
                .sessionId(blankToNull(request.sessionId()))
                .build();
        context.put(AgentSpawnTool.CTX_FORCE_SYNC, Boolean.TRUE);
        EventCodec codec = new EventCodec();
        Disposable disposable;
        if (request.structured()) {
            disposable = structuredStream(inbound, context, request)
                    .timeout(callTimeout)
                    .subscribe(
                            event -> codec.toStreamEvent(event)
                                    .ifPresent(mapped -> handler.onEvent(withPlan(mapped, context))),
                            error -> handler.onError(mapStreamError(error)),
                            handler::onComplete);
        } else {
            disposable = agent.streamEvents(List.of(inbound), context)
                    .timeout(callTimeout)
                    .subscribe(
                            asEvent -> codec.toDomain(asEvent)
                                    .ifPresent(mapped -> handler.onEvent(withPlan(mapped, context))),
                            error -> handler.onError(mapStreamError(error)),
                            handler::onComplete);
        }
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

    private Flux<Event> structuredStream(Msg inbound, RuntimeContext context, AgentInvokeRequest request) {
        JsonNode schema = MessageCodec.jsonSchemaNode(request.jsonSchema());
        if (schema != null) {
            return agent.stream(List.of(inbound), StreamOptions.defaults(), schema, context);
        }
        return agent.stream(List.of(inbound), StreamOptions.defaults(), Map.class, context);
    }

    @Override
    public void close() {
        try {
            agent.close();
        } finally {
            if (redisClient != null) {
                try {
                    redisClient.close();
                } catch (Exception ex) {
                    throw new IllegalStateException("cannot close redis", ex);
                }
            }
        }
    }

    private static Path tempWorkspace() {
        try {
            Path dir = Files.createTempDirectory("dream-scope-ws-");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException ex) {
            throw new IllegalStateException("cannot create temp workspace", ex);
        }
    }

    private AgentEvent withPlan(AgentEvent event, RuntimeContext context) {
        if (!(event instanceof AgentEvent.Done done) || !planModeEnabled) {
            return event;
        }
        return new AgentEvent.Done(
                done.finalOutput(),
                done.inputTokens(),
                done.outputTokens(),
                done.data(),
                agent.isPlanModeActive(context));
    }

    private static Path blankPathToNull(Path path) {
        if (path == null || path.toString().isBlank()) {
            return null;
        }
        return path;
    }

    private static Duration normalizeTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return DEFAULT_TIMEOUT;
        }
        return timeout;
    }

    private RuntimeException mapStreamError(Throwable error) {
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
