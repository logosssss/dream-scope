package com.zhu.scope.adapter;

import com.zhu.scope.adapter.event.EventCodec;
import com.zhu.scope.adapter.middleware.ChatOtel;
import com.zhu.scope.adapter.middleware.LoggingMiddleware;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import com.zhu.scope.adapter.mcp.ChatMcp;
import com.zhu.scope.adapter.mcp.ChatMcpServer;
import com.zhu.scope.adapter.nacos.ChatNacosClient;
import com.zhu.scope.adapter.subagent.ChatSubagents;
import com.zhu.scope.adapter.tool.ChatTools;
import com.zhu.scope.agent.AgentEvent;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentTrace;
import com.zhu.scope.agent.AgentTraceStep;
import com.zhu.scope.agent.AgentProviderException;
import com.zhu.scope.agent.AgentStreamHandler;
import com.zhu.scope.agent.AgentTimeoutException;
import com.zhu.scope.agent.StreamingAgentHandler;
import com.zhu.scope.knowledge.RetrievePort;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import redis.clients.jedis.JedisPooled;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

/**
 * 内置 {@code chat} Agent：对外是 domain {@link StreamingAgentHandler}，对内是 AgentScope {@link HarnessAgent}。
 *
 * <p>无 Spring。web {@code PortsConfig} / boot 组合根调用 {@link #create} 注入模型、Redis、workspace、压缩、Plan Mode、
 * {@link RetrievePort}。本类是 adapter 里框架类型的集中点，web / domain 只看到 {@link AgentInvokeRequest} /
 * {@link AgentEvent} / {@link AgentInvokeResult}。
 *
 * <h2>两条构造路径</h2>
 *
 * <ul>
 *   <li>{@link #create}：生产。{@link ChatRedis#open} ping 失败则进程起不来；会话走
 *       {@code RedisDistributedStore}，不用 JSON 文件 store。
 *   <li>公开构造函数：单测。可注入 {@link AgentStateStore}，workspace 默认临时目录，不连 Redis。
 * </ul>
 *
 * <h2>{@link HarnessAgent.Builder} 本仓库怎么填</h2>
 *
 * <pre>
 * .name                     AgentIds.CHAT（"chat"）
 * .sysPrompt                SYS_PROMPT，Plan Mode 开启时再拼 PLAN_PROMPT；Nacos Prompt 走 onSystemPrompt 中间件
 * .skillRepository          可选 NacosSkillRepository（与 workspace skills/ 并存）
 * .model / .fallbackModel   ModelRegistry；fallback 与主模型 id 相同则不加
 * .generateOptions          temperature / topP / maxTokens 全空则不调
 * .toolkit                  ChatTools + 可选 MCP（McpClientBuilder.registerMcpClient）+ Harness agent_spawn
 * .disableFilesystemTools   关掉 read_file / write_file 等默认工作区文件工具
 * .permissionContext        BYPASS：HTTP 没有确认通道，plan_exit / 非只读 MCP 直接执行
 * .disableShellTool         HTTP 进程禁止 shell
 * .compaction / .memory     条数阈值 + MemoryConfig.defaults()
 * .subagents                ChatSubagents.programmatic()；md 子 Agent 另由 workspace 扫描
 * .middlewares              OtelTracingMiddleware + LoggingMiddleware
 * .distributedStore         生产 Redis；与 .stateStore 互斥（有 Redis 就不走文件 store）
 * .workspace                本地目录：AGENTS.md / knowledge / MEMORY.md / skills / subagents / tools.json
 * .enablePlanMode           仅 planModeEnabled 且 workspace 非空时打开
 * </pre>
 *
 * <h2>一次调用</h2>
 *
 * <p>{@link #handle} 不是另一条裸 {@code agent.call()}。普通请求订阅 {@link #streamHandle} 的 {@code streamEvents}，拼
 * {@code TextDelta}，优先用 {@link AgentEvent.Done#finalOutput()}。{@code structured=true} 走 {@code call(..., schema|Class)}
 * （2.0.3 的 {@code streamEvents} 没有 schema 重载；旧 {@code stream()} 已 forRemoval），再编成一条 {@link AgentEvent.Done}。
 *
 * <p>会话：{@code userId} 与 {@code sessionId} <em>成对非空</em> 才写入 Redis 槽位；只传一个等于无会话。
 */
public final class ScopeChatAgent implements StreamingAgentHandler, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ScopeChatAgent.class);

    /** yml / 环境都没配模型时的兜底，对应 {@code DASHSCOPE_API_KEY}。 */
    static final String DEFAULT_MODEL_ID = "dashscope:qwen-plus";

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    /**
     * 主系统提示：演示工具 + 子 Agent 路由 + Skill + RAG + MCP。
     *
     * <p>天气 / 航班 / 摘要必须 {@code agent_spawn}，禁止模型自己编。产品问答走 {@code retrieve}。MCP 用服务端公布的工具名
     * （{@link ChatMcp} / {@code registerMcpClient}），本机演示是 {@code mcp__demo__echo}。
     */
    private static final String SYS_PROMPT =
            "你是一个有帮助的助手。需要当前时间、四则运算或抓取网页时，调用 getCurrentTime / calculate / httpGet。"
                    + "当用户询问天气时，调用 agent_spawn，agent_id 为 weather-agent，task 写明城市与日期。"
                    + "当用户询问航班时，调用 agent_spawn，agent_id 为 flight-agent，task 写明出发地、目的地与日期。"
                    + "当用户要求总结、摘要或缩写一段话时，调用 agent_spawn，agent_id 为 summarizer，task 为待处理原文。"
                    + "子 Agent 返回的是演示结果，转述给用户即可；不要自己编造天气、航班或摘要。"
                    + "当用户要求写会议纪要、讨论要点或待办清单时，先查看 available_skills，调用 load_skill_through_path 加载 meeting-notes 的 SKILL.md，再按其中步骤整理。"
                    + "回答与 dream-scope 产品、架构或调用方式有关的问题时，先调用 retrieve，按返回的 [1][2] 引用，不要编造。"
                    + "工具表里的 MCP 工具用服务端公布的名字，看到就按需调用。";

    /** 拼在 SYS_PROMPT 后；未开 Plan Mode 不加，避免模型无工具还尝试 {@code plan_enter}。 */
    private static final String PLAN_PROMPT =
            "复杂任务可先调用 plan_enter，用 plan_write 写下步骤，然后直接 plan_exit 执行。普通问答不必进入计划模式。";

    private final HarnessAgent agent;

    private final Duration callTimeout;

    /** 生产是 {@link JedisPooled}；单测构造为 null。{@link #close} 在 agent 之后关。 */
    private final AutoCloseable redisClient;

    /** 生产 MCP 连接；单测为空。{@link #close} 在 Harness 之后、Redis 之前关。 */
    private final List<McpClientWrapper> mcpClients;

    private final boolean planModeEnabled;

    /** 单测：默认超时、临时 workspace、无 Redis。 */
    public ScopeChatAgent(Model model) {
        this(model, DEFAULT_TIMEOUT, null, tempWorkspace());
    }

    /** 单测：可注入 {@link AgentStateStore}（文件会话），仍不连 Redis。 */
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
                "plans",
                null,
                List.of(),
                null,
                null);
    }

    /**
     * 真正 {@code HarnessAgent.builder().build()} 的入口。公开构造与 {@link #create} 都汇到这里。
     *
     * <p>{@code distributedStore} 与 {@code stateStore} 二选一：生产传 Redis；测试传文件 store。两者都空则框架用内存态，
     * 进程重启会话丢失。Plan Mode 依赖 workspace 落盘，没有工作区就不开。
     */
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
            String planDirectory,
            RetrievePort retrievePort,
            List<McpClientWrapper> mcpClients,
            String sysPromptOverride,
            ChatNacosClient nacos) {
        ChatOtel.install();
        this.callTimeout = normalizeTimeout(callTimeout);
        this.redisClient = redisClient;
        this.planModeEnabled = planModeEnabled;
        this.mcpClients = mcpClients == null || mcpClients.isEmpty() ? List.of() : List.copyOf(mcpClients);
        String base = SYS_PROMPT;
        if (sysPromptOverride != null && !sysPromptOverride.isBlank()) {
            base = SYS_PROMPT + "\n" + sysPromptOverride.trim();
        }
        String prompt = planModeEnabled ? base + PLAN_PROMPT : base;
        List<MiddlewareBase> extras = new ArrayList<>();
        AgentSkillRepository nacosSkills = null;
        if (nacos != null) {
            MiddlewareBase promptMw = nacos.promptMiddleware();
            if (promptMw != null) {
                extras.add(promptMw);
            }
            nacosSkills = nacos.skillRepository();
        }
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(AgentIds.CHAT)
                .sysPrompt(prompt)
                .model(Objects.requireNonNull(model, "model"))
                .toolkit(chatToolkit(retrievePort, this.mcpClients))
                .compaction(compactionConfig(compactionTriggerMessages, compactionKeepMessages))
                .memory(MemoryConfig.defaults())
                .subagents(chatSubagents())
                .middlewares(chatMiddlewares(extras))
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build())
                .disableFilesystemTools()
                .disableShellTool();
        if (nacosSkills != null) {
            builder.skillRepository(nacosSkills);
        }
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
        //Plan Mode
        if (planModeEnabled && hasWorkspace) {
            builder.enablePlanMode()
                    .planFileDirectory(
                            planDirectory == null || planDirectory.isBlank() ? "plans" : planDirectory);
        }
        this.agent = builder.build();
        log.info(
                "chat harness ready model={} timeout={} workspace={} planMode={} mcp={} nacosPrompt={} nacosSkill={}",
                model.getModelName(),
                this.callTimeout,
                hasWorkspace ? workspace : "-",
                planModeEnabled && hasWorkspace,
                this.mcpClients.size(),
                !extras.isEmpty(),
                nacosSkills != null);
    }

    /**
     * 生产装配（无检索）：模型 + Redis DistributedStore + workspace + 压缩/记忆。
     *
     * @see #create(String, String, ChatHarnessOptions, RetrievePort)
     */
    public static ScopeChatAgent create(String modelId, String apiKey, ChatHarnessOptions options) {
        return create(modelId, apiKey, options, null);
    }

    /**
     * 生产装配。先 {@link ChatRedis#open}（含 ping），build 失败必须关掉 Jedis，避免连接泄漏。
     *
     * @param retrievePort 可空；非空时 {@link ChatTools} 注册 {@code retrieve}
     */
    public static ScopeChatAgent create(
            String modelId, String apiKey, ChatHarnessOptions options, RetrievePort retrievePort) {
        return create(modelId, apiKey, options, retrievePort, List.of());
    }

    /**
     * 生产装配。MCP 走官方 {@link ChatMcp}（{@code McpClientBuilder} + {@code Toolkit.registerMcpClient}），
     * 不写进 workspace {@code tools.json}，避免 Harness 再连一次。
     */
    public static ScopeChatAgent create(
            String modelId,
            String apiKey,
            ChatHarnessOptions options,
            RetrievePort retrievePort,
            List<ChatMcpServer> mcpServers) {
        return create(modelId, apiKey, options, retrievePort, mcpServers, null);
    }

    /**
     * 生产装配。{@code nacos} 非空时挂 Prompt {@code onSystemPrompt} 中间件，以及可选 {@code NacosSkillRepository}。
     */
    public static ScopeChatAgent create(
            String modelId,
            String apiKey,
            ChatHarnessOptions options,
            RetrievePort retrievePort,
            List<ChatMcpServer> mcpServers,
            ChatNacosClient nacos) {
        Objects.requireNonNull(options, "options");
        JedisPooled jedis = ChatRedis.open(options.redisUri());
        List<McpClientWrapper> mcpClients = List.of();
        try {
            mcpClients = ChatMcp.openAll(mcpServers);
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
                    options.planDirectory(),
                    retrievePort,
                    mcpClients,
                    options.sysPromptOverride(),
                    nacos);
        } catch (RuntimeException ex) {
            ChatMcp.closeQuietly(mcpClients);
            jedis.close();
            throw ex;
        }
    }

    /** {@code provider:model} → {@link ModelRegistry}。apiKey 由调用方从配置传入。 */
    static Model resolveModel(String modelId, String apiKey) {
        String id = resolveModelId(modelId, null);
        ModelCreationContext context = ModelCreationContext.builder().apiKey(apiKey).build();
        return ModelRegistry.resolve(id, context);
    }

    /**
     * 主模型失败时的第二个 {@link Model}。id 与主模型相同则不加（没意义）；配了 fallback 却没有 Key 直接失败，
     * 避免运行时才发现。
     */
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

    /** 三个采样参数都空 → 不调 {@code generateOptions}，沿用模型默认。 */
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

    /**
     * 按消息条数压缩。{@code keepTokens(0)} 关掉 token 阈值，避免和条数阈值缠在一起。
     * {@code trigger}/{@code keep} ≤0 时回落到 {@link ChatHarnessOptions} 默认 30 / 10。
     */
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
        return chatToolkit(null);
    }

    /**
     * 注册 {@link ChatTools}，再按官方路径挂 MCP。Harness 还会注入 {@code agent_spawn}；文件/shell 已 disable。
     * {@code retrievePort == null} 时 {@code retrieve} 工具会拒绝调用，而不是从工具表消失（见 ChatTools）。
     */
    static Toolkit chatToolkit(RetrievePort retrievePort) {
        return chatToolkit(retrievePort, List.of());
    }

    static Toolkit chatToolkit(RetrievePort retrievePort, List<McpClientWrapper> mcpClients) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ChatTools(retrievePort));
        ChatMcp.register(toolkit, mcpClients);
        return toolkit;
    }

    /**
     * 编程式子 Agent。Markdown 声明仍由 Harness 扫描 workspace {@code subagents/*.md}，{@code build()} 时两路合并。
     * 不要给同一个 {@code agent_id} 写两种声明。
     */
    static List<SubagentDeclaration> chatSubagents() {
        return ChatSubagents.programmatic();
    }

    /**
     * chat 横切：手册 {@link OtelTracingMiddleware}（读 GlobalOpenTelemetry）+ 本仓库 {@link LoggingMiddleware}。
     * Middleware 类型不得漏到 web / domain。
     */
    static List<MiddlewareBase> chatMiddlewares() {
        return chatMiddlewares(List.of());
    }

    static List<MiddlewareBase> chatMiddlewares(List<MiddlewareBase> extra) {
        List<MiddlewareBase> out = new ArrayList<>();
        out.add(new OtelTracingMiddleware());
        out.add(new LoggingMiddleware());
        if (extra != null) {
            out.addAll(extra);
        }
        return List.copyOf(out);
    }

    /**
     * {@code dream-scope.model.chat} 优先，否则 {@code model.default}，再否则 {@link #DEFAULT_MODEL_ID}。
     */
    public static String resolveModelId(String chat, String fallback) {
        if (chat != null && !chat.isBlank()) {
            return chat.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return DEFAULT_MODEL_ID;
    }

    /**
     * 模型 id 前缀 → 历史环境变量名。8091 已改为读 {@code dream-scope.model.*-api-key}。
     */
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

    /**
     * 同步 {@code POST /api/agents/invoke}。内部仍走 {@link #streamHandle}，等到 {@code onComplete} 再返回。
     *
     * <p>等待上限是 {@code callTimeout + 5s}：流上已有 {@code .timeout(callTimeout)}，这里多留一点给收尾的 {@code Done}。
     * 优先 {@link AgentEvent.Done} 的正文 / token / structured / planActive；没有 Done 才用拼起来的 TextDelta。
     * toolCall、toolResult、hint、error 按到达顺序放进 {@code trace}。
     *
     * <p>等待失败、超时或线程中断时取消订阅并 {@code interrupt}，避免 HTTP 已返回后模型、工具和子 Agent 还在跑。
     * Reactor {@code timeout} 只看相邻事件的空闲间隔，持续有事件时靠这里的总时限收口。
     */
    @Override
    public AgentInvokeResult handle(AgentInvokeRequest request) {
        CompletableFuture<AgentInvokeResult> done = new CompletableFuture<>();
        InvokeCollector collector = new InvokeCollector(done);
        streamHandle(request, collector);
        try {
            AgentInvokeResult result = done.get(callTimeout.plusSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            log.info(
                    "chat invoke done sessionId={} outputChars={} inTokens={} outTokens={} planActive={}",
                    request.sessionId(),
                    LogText.chars(result.output()),
                    result.inputTokens(),
                    result.outputTokens(),
                    result.planActive());
            return result;
        } catch (InterruptedException ex) {
            collector.cancel();
            Thread.currentThread().interrupt();
            log.warn("chat invoke interrupted sessionId={}", request.sessionId());
            throw new AgentProviderException("chat model call interrupted", ex);
        } catch (TimeoutException ex) {
            collector.cancel();
            log.warn("chat invoke timeout sessionId={} timeout={}", request.sessionId(), callTimeout);
            throw new AgentTimeoutException("chat timed out after " + callTimeout, ex);
        } catch (ExecutionException ex) {
            collector.cancel();
            log.warn("chat invoke failed sessionId={} message={}", request.sessionId(), ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
            throw mapStreamError(ex.getCause() == null ? ex : ex.getCause());
        }
    }

    /**
     * SSE {@code POST /api/agents/stream} 的主路径。每条订阅 {@code new EventCodec()}，不要复用。
     *
     * <p>{@link AgentSpawnTool#CTX_FORCE_SYNC}：子 Agent 同步跑完再继续主循环，避免 HTTP 超时后后台还在 spawn。
     *
     * <p>订阅后调用 {@link AgentStreamHandler#bindCancel}，把 {@code dispose + interrupt} 绑上去。默认实现忽略。
     */
    @Override
    public void streamHandle(AgentInvokeRequest request, AgentStreamHandler handler) {
        Objects.requireNonNull(handler, "handler");
        log.info(
                "chat stream start sessionId={} userId={} structured={} images={} inputChars={} preview={}",
                request.sessionId(),
                request.userId(),
                request.structured(),
                request.imageUrls() == null ? 0 : request.imageUrls().size(),
                LogText.chars(request.input()),
                LogText.preview(request.input()));
        Msg inbound = MessageCodec.toUserMessage(request.input(), request.imageUrls());
        RuntimeContext context = RuntimeContext.builder()
                .userId(blankToNull(request.userId()))
                .sessionId(blankToNull(request.sessionId()))
                .build();
        context.put(AgentSpawnTool.CTX_FORCE_SYNC, Boolean.TRUE);
        EventCodec codec = new EventCodec();
        Disposable disposable;
        if (request.structured()) {
            disposable = structuredCall(inbound, context, request)
                    .timeout(callTimeout)
                    .subscribe(
                            msg -> handler.onEvent(withPlan(codec.toDone(msg), context)),
                            error -> {
                                log.warn(
                                        "chat stream error sessionId={} structured=true message={}",
                                        request.sessionId(),
                                        error == null ? "" : error.getMessage());
                                handler.onError(mapStreamError(error));
                            },
                            () -> {
                                log.info("chat stream complete sessionId={} structured=true", request.sessionId());
                                handler.onComplete();
                            });
        } else {
            disposable = agent.streamEvents(List.of(inbound), context)
                    .timeout(callTimeout)
                    .subscribe(
                            asEvent -> codec.toDomain(asEvent)
                                    .ifPresent(mapped -> handler.onEvent(withPlan(mapped, context))),
                            error -> {
                                log.warn(
                                        "chat stream error sessionId={} message={}",
                                        request.sessionId(),
                                        error == null ? "" : error.getMessage());
                                handler.onError(mapStreamError(error));
                            },
                            () -> {
                                log.info("chat stream complete sessionId={}", request.sessionId());
                                handler.onComplete();
                            });
        }
        handler.bindCancel(() -> {
            disposable.dispose();
            try {
                agent.interrupt(context);
            } catch (RuntimeException ignored) {
                // 订阅已取消即可
            }
        });
    }

    /**
     * 结构化输出：2.0.3 只有 {@code call(List, JsonNode|Class, RuntimeContext)} 能带 schema。
     * {@code streamEvents} 无此重载；{@code stream()} 自 2.0.0 forRemoval。有 JSON Schema 用 schema，否则 {@code Map.class}。
     */
    private Mono<Msg> structuredCall(Msg inbound, RuntimeContext context, AgentInvokeRequest request) {
        List<Msg> messages = List.of(inbound);
        JsonNode schema = MessageCodec.jsonSchemaNode(request.jsonSchema());
        if (schema != null) {
            return agent.call(messages, schema, context);
        }
        return agent.call(messages, Map.class, context);
    }

    /** 先关 Harness，再关 MCP 连接/子进程，最后关 Jedis。 */
    @Override
    public void close() {
        try {
            agent.close();
        } finally {
            ChatMcp.closeQuietly(mcpClients);
            if (redisClient != null) {
                try {
                    redisClient.close();
                } catch (Exception ex) {
                    throw new IllegalStateException("cannot close redis", ex);
                }
            }
        }
    }

    /** 单测无配置 workspace 时用；{@code deleteOnExit} 不保证立刻删掉，只避免磁盘常驻。 */
    private static Path tempWorkspace() {
        try {
            Path dir = Files.createTempDirectory("dream-scope-ws-");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException ex) {
            throw new IllegalStateException("cannot create temp workspace", ex);
        }
    }

    /**
     * {@link EventCodec} 不知道 Plan Mode。只在 {@link AgentEvent.Done} 上补 {@code planActive}，其它事件原样转发。
     */
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

    /** 流错误收成 domain 异常：已是 {@link AgentTimeoutException} / {@link AgentProviderException} 则原样，超时链再包一层。 */
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

    /**
     * Reactor {@code .timeout()} 可能是 {@link TimeoutException}，也可能是阻塞读上的 {@link IllegalStateException}。
     * 沿 cause 链找，避免被包一层后当成普通 502。
     */
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

    /** 空白当没传。会话槽位要求 userId、sessionId 都非空，单边空白必须变成 null 交给框架。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 同步 invoke 的事件收集器。覆盖 {@link AgentStreamHandler#bindCancel}，让 {@link #streamHandle} 把 dispose + interrupt 绑过来。
     */
    private final class InvokeCollector implements AgentStreamHandler {

        private final CompletableFuture<AgentInvokeResult> done;

        private final StringBuilder deltas = new StringBuilder();

        private final List<AgentTraceStep> trace = new ArrayList<>();

        private volatile Runnable cancelTask = () -> {};

        private volatile String lastDone = "";

        private volatile int inputTokens;

        private volatile int outputTokens;

        private volatile Map<String, Object> data;

        private volatile boolean planActive;

        private InvokeCollector(CompletableFuture<AgentInvokeResult> done) {
            this.done = done;
        }

        @Override
        public void bindCancel(Runnable cancel) {
            this.cancelTask = cancel == null ? () -> {} : cancel;
        }

        void cancel() {
            try {
                cancelTask.run();
            } catch (RuntimeException ex) {
                log.warn("chat invoke cancel failed: {}", ex.getMessage());
            }
        }

        @Override
        public void onEvent(AgentEvent event) {
            if (event instanceof AgentEvent.TextDelta delta && delta.text() != null) {
                deltas.append(delta.text());
            }
            AgentTraceStep step = AgentTrace.from(event);
            if (step != null) {
                trace.add(step);
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
            done.complete(new AgentInvokeResult(
                    id(), text, inputTokens, outputTokens, data, planActive, List.copyOf(trace)));
        }

        @Override
        public void onError(Throwable error) {
            done.completeExceptionally(error);
        }
    }
}
