package com.zhu.scope.adapter.event;

import com.zhu.scope.adapter.MessageCodec;
import com.zhu.scope.agent.AgentEvent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * AgentScope 框架事件 → domain {@link AgentEvent}。web / domain 只认后者，不得出现 {@code io.agentscope}。
 *
 * <h2>为何有状态、为何一次订阅一个实例</h2>
 *
 * <p>AgentScope 2.0.3 把一次工具调用拆成多条细事件：start → 若干 delta → end。{@code ToolCallEndEvent} /
 * {@code ToolResultEndEvent} <em>没有</em>完整 input / output getter，只有 id 与工具名。因此本类按
 * {@code toolCallId} 把 delta 文本攒在 {@link #toolInputs} / {@link #toolOutputs}，到 end 再弹出一条对外事件。
 *
 * <p>用量同理：{@link ModelCallEndEvent}（以及 {@link #toDone} / {@link #toStreamEvent} 里 {@link Msg#getChatUsage()}）
 * 只负责累加，不单独对外发；token 挂到最终的 {@link AgentEvent.Done}。
 *
 * <p>Reactor 订阅线程对同一流通常是串行投递，本类<strong>非线程安全</strong>。{@code ScopeChatAgent} /
 * {@code StarterChatAgent} 每次 {@code streamHandle} 都 {@code new EventCodec()}，不要跨请求复用。
 *
 * <h2>两条入口对应两条 AgentScope 流</h2>
 *
 * <ul>
 *   <li>{@link #toDomain}：{@code agent.streamEvents(...)} 的类型化 {@code io.agentscope.core.event.AgentEvent}
 *       （产品路径默认走这里；boot 的 starter 路径也走这里）。
 *   <li>{@link #toDone}：结构化输出走 {@code agent.call(..., schema|Class)}，只有最终 {@link Msg}，没有类型化事件流。
 *   <li>{@link #toStreamEvent}：已弃用的粗粒度 {@link Event} 流；2.0.3 的 {@code stream()} 标了 forRemoval，产品路径不再调用。
 * </ul>
 *
 * <h2>{@link #toDomain} 映射（未列出的生命周期事件直接丢弃）</h2>
 *
 * <pre>
 * TextBlockDeltaEvent        → TextDelta（空 delta 丢弃）
 * ToolCallStartEvent         → empty（只记工具名）
 * ToolCallDeltaEvent         → empty（记名 + 拼 input）
 * ToolCallEndEvent           → ToolCall(name, 累积 input)
 * ToolResultTextDeltaEvent   → empty（记名 + 拼 output）
 * ToolResultEndEvent         → ToolResult(name, 累积 output)
 * HintBlockEvent             → Hint（Plan Mode 等提示；空文本丢弃）
 * ModelCallEndEvent          → empty（累加 input/output tokens）
 * AgentResultEvent           → Done(正文, 累积 tokens, 结构化 Map)
 * ExceedMaxItersEvent        → Error
 * </pre>
 *
 * <p>{@link Optional#empty()} 表示「框架内部态，不要推给 SSE / {@code AgentStreamHandler}」。
 */
public final class EventCodec {

    /** toolCallId → 最近一次非空工具名；end 事件名缺失时回填。 */
    private final Map<String, String> toolNames = new HashMap<>();

    /** toolCallId → 尚未对外发出的工具入参碎片（JSON 等）。end 时 {@link #take} 弹出。 */
    private final Map<String, StringBuilder> toolInputs = new HashMap<>();

    /** toolCallId → 尚未对外发出的工具出参碎片。end 时弹出。 */
    private final Map<String, StringBuilder> toolOutputs = new HashMap<>();

    /** 本轮流式调用累计的 prompt tokens，写入最终 {@link AgentEvent.Done}。 */
    private int inputTokens;

    /** 本轮流式调用累计的 completion tokens。 */
    private int outputTokens;

    /**
     * 类型化 AgentEvent 流（{@code streamEvents}）→ domain。
     *
     * <p>中间态返回 empty，由本实例记住碎片；只有对 SSE 有意义的形状才 {@code Optional.of}。
     *
     * @param event AgentScope 事件，允许 null（当作 empty，避免订阅侧 NPE）
     */
    public Optional<AgentEvent> toDomain(io.agentscope.core.event.AgentEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        return switch (event) {
            case TextBlockDeltaEvent e -> emitText(e.getDelta(), AgentEvent.TextDelta::new);
            case ToolCallStartEvent e -> rememberTool(e.getToolCallId(), e.getToolCallName());
            case ToolCallDeltaEvent e ->
                    bufferTool(toolInputs, e.getToolCallId(), e.getToolCallName(), e.getDelta());
            case ToolCallEndEvent e -> Optional.of(new AgentEvent.ToolCall(
                    finishTool(e.getToolCallId(), e.getToolCallName()), take(toolInputs, e.getToolCallId())));
            case ToolResultTextDeltaEvent e ->
                    bufferTool(toolOutputs, e.getToolCallId(), e.getToolCallName(), e.getDelta());
            case ToolResultEndEvent e -> Optional.of(new AgentEvent.ToolResult(
                    finishTool(e.getToolCallId(), e.getToolCallName()), take(toolOutputs, e.getToolCallId())));
            case HintBlockEvent e -> emitText(e.getHint(), AgentEvent.Hint::new);
            case ModelCallEndEvent e -> {
                accumulateUsage(e.getUsage());
                yield Optional.empty();
            }
            case AgentResultEvent e -> Optional.of(doneOf(e.getResult()));
            case ExceedMaxItersEvent e -> Optional.of(new AgentEvent.Error(
                    "exceeded max iterations: " + e.getCurrentIter() + "/" + e.getMaxIters()));
            default -> Optional.empty();
        };
    }

    /**
     * {@code agent.call(..., schema|Class)} 的最终 {@link Msg} → {@link AgentEvent.Done}。
     *
     * <p>2.0.3 的 {@code streamEvents} 没有 schema 重载；结构化必须走 {@code call}。这条路径没有
     * {@link ModelCallEndEvent}，用量从 {@link Msg#getChatUsage()} 取。
     */
    public AgentEvent.Done toDone(Msg msg) {
        if (msg != null) {
            accumulateUsage(msg.getChatUsage());
        }
        return doneOf(msg);
    }

    /**
     * 粗粒度 {@link Event} 流 → domain。对应已 forRemoval 的 {@code agent.stream(...)}，产品路径已不再调用。
     *
     * <p>{@link Event#getType()} 只有少数枚举对前端有意义：推理文本、工具结果、最终结果。其它类型（如中间
     * TOOL_CALL）没有完整增量协议，这里直接丢弃，避免半截 JSON 冒出去。
     *
     * <p>每条带 {@link Msg} 的事件都尝试累加 {@link ChatUsage}，因为这条流没有独立的 {@link ModelCallEndEvent}。
     *
     * @param event AgentScope {@code Event}，null 或 type 为 null 时 empty
     */
    public Optional<AgentEvent> toStreamEvent(Event event) {
        if (event == null || event.getType() == null) {
            return Optional.empty();
        }
        Msg msg = event.getMessage();
        if (msg != null) {
            accumulateUsage(msg.getChatUsage());
        }
        return switch (event.getType()) {
            case REASONING -> emitText(MessageCodec.textOf(msg), AgentEvent.TextDelta::new);
            case TOOL_RESULT -> Optional.of(new AgentEvent.ToolResult(msgName(msg), MessageCodec.textOf(msg)));
            case AGENT_RESULT -> Optional.of(doneOf(msg));
            default -> Optional.empty();
        };
    }

    private AgentEvent.Done doneOf(Msg msg) {
        return new AgentEvent.Done(
                MessageCodec.textOf(msg), inputTokens, outputTokens, MessageCodec.structuredOf(msg));
    }

    private static Optional<AgentEvent> emitText(String text, Function<String, AgentEvent> factory) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(factory.apply(text));
    }

    private Optional<AgentEvent> rememberTool(String toolCallId, String toolName) {
        rememberName(toolCallId, toolName);
        return Optional.empty();
    }

    private Optional<AgentEvent> bufferTool(
            Map<String, StringBuilder> buffers, String toolCallId, String toolName, String delta) {
        rememberName(toolCallId, toolName);
        append(buffers, toolCallId, delta);
        return Optional.empty();
    }

    private String finishTool(String toolCallId, String toolName) {
        rememberName(toolCallId, toolName);
        return nameOf(toolCallId, toolName);
    }

    private static String msgName(Msg msg) {
        return msg == null || msg.getName() == null ? "" : msg.getName();
    }

    /** 把一次模型调用的用量累加到本订阅；null / 负值按 0 处理，避免脏数据把 Done 打成负数。 */
    private void accumulateUsage(ChatUsage usage) {
        if (usage == null) {
            return;
        }
        inputTokens += Math.max(0, usage.getInputTokens());
        outputTokens += Math.max(0, usage.getOutputTokens());
    }

    /** 只在工具名非空时覆盖缓存；空名保留 start/delta 里已经记下的值。 */
    private void rememberName(String toolCallId, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        toolNames.put(key(toolCallId), toolName);
    }

    /**
     * 优先用当前事件自带的工具名；缺失则查 {@link #toolNames}。
     *
     * <p>end 事件偶发不带 name，而 start/delta 带了，所以不能只读 end 上的 getter。
     */
    private String nameOf(String toolCallId, String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            return toolName;
        }
        String remembered = toolNames.get(key(toolCallId));
        return remembered == null ? "" : remembered;
    }

    /** 把非空 delta 接到对应 toolCallId 的缓冲；id 缺失时落到 {@link #key} 的占位键。 */
    private static void append(Map<String, StringBuilder> buffers, String toolCallId, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        buffers.computeIfAbsent(key(toolCallId), ignored -> new StringBuilder()).append(delta);
    }

    /**
     * 取出并删除该 id 的缓冲，让同一次订阅里后续同 id 的工具调用从空串重新攒。
     *
     * @return 从未出现过 delta 时返回 {@code ""}，而不是 null（domain record 侧也不收 null 碎片）
     */
    private static String take(Map<String, StringBuilder> buffers, String toolCallId) {
        StringBuilder buffer = buffers.remove(key(toolCallId));
        return buffer == null ? "" : buffer.toString();
    }

    /**
     * Map 键：空白 {@code toolCallId} 收成 {@code "_"}，避免 {@code HashMap} 里同时出现 null 键和空串键，
     * 把同一轮「没带 id 的碎片」拆到两个桶。
     */
    private static String key(String toolCallId) {
        return toolCallId == null || toolCallId.isBlank() ? "_" : toolCallId;
    }
}
