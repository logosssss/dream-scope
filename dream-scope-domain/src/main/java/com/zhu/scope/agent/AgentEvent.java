package com.zhu.scope.agent;

import java.util.Map;

/**
 * 对外流式事件（domain SPI）。不含任何 {@code io.agentscope} 类型；adapter 的 {@code EventCodec}
 * 把框架事件编成这些形状，web / boot 再编成 SSE。
 *
 * <h2>为何 sealed</h2>
 *
 * <p>许可的 6 种形状与 HTTP SSE 的 {@code event:} 名一一对应，web 侧 {@code switch} 必须穷尽，新增一种
 * 会在编译期打断，避免漏推前端。
 *
 * <pre>
 * TextDelta  → event: textDelta   { type, text }
 * ToolCall   → event: toolCall    { type, toolName, input }
 * ToolResult → event: toolResult  { type, toolName, output }
 * Hint       → event: hint        { type, text }
 * Done       → event: done        { type, finalOutput, inputTokens, outputTokens, planActive, data? }
 * Error      → event: error       { type, message }
 * </pre>
 *
 * <h2>谁生产、谁消费</h2>
 *
 * <ul>
 *   <li>生产：{@code EventCodec.toDomain} / {@code toStreamEvent}（chat）；知识库 handler 直接
 *       {@code new TextDelta} + {@code new Done}。
 *   <li>消费：{@link AgentStreamHandler#onEvent}。web {@code SseBridge} 按上面的表推送；同步
 *       {@code /invoke} 则把流上的 {@link Done} 收成 {@link AgentInvokeResult}。
 * </ul>
 *
 * <p>框架内部态（工具 start/delta、{@code ModelCallEndEvent} 等）不会变成 {@code AgentEvent}，由 codec
 * 攒齐后再发 {@link ToolCall} / {@link ToolResult} / {@link Done}。
 */
public sealed interface AgentEvent
        permits AgentEvent.TextDelta,
                AgentEvent.ToolCall,
                AgentEvent.ToolResult,
                AgentEvent.Hint,
                AgentEvent.Done,
                AgentEvent.Error {

    /**
     * 模型正文增量。对应 SSE {@code textDelta}。
     *
     * <p>{@code text} 为这一小片增量，不是全文。空串不应出现：codec 在源头丢掉空 delta。
     *
     * @param text 本片增量文本
     */
    record TextDelta(String text) implements AgentEvent {}

    /**
     * 一次工具调用的<strong>完整</strong>入参。对应 SSE {@code toolCall}。
     *
     * <p>AgentScope 2.0.3 入参是 start + 若干 delta + end；对外只在 end 发这一条，{@code input} 已是
     * 拼好的 JSON（或其它文本）。{@code toolName} 缺失时 codec 用空串，避免 SSE 出现 null。
     *
     * @param toolName 工具名（如 {@code calculate}）
     * @param input    完整入参文本
     */
    record ToolCall(String toolName, String input) implements AgentEvent {}

    /**
     * 一次工具调用的<strong>完整</strong>出参。对应 SSE {@code toolResult}。
     *
     * <p>与 {@link ToolCall} 对称：delta 在 codec 内累积，end 时弹出。失败态目前不单独建模，输出文本里带错误信息。
     *
     * @param toolName 工具名
     * @param output   完整出参文本
     */
    record ToolResult(String toolName, String output) implements AgentEvent {}

    /**
     * 计划 / 提示块。对应 SSE {@code hint}。
     *
     * <p>Plan Mode 开启时，框架 {@code HintBlockEvent} 编成本类型，给前端展示「先写计划」一类提示，
     * 不是模型最终答复。紧凑构造把 {@code null} 收成空串，与其它字符串字段一致。
     *
     * @param text 提示正文
     */
    record Hint(String text) implements AgentEvent {
        public Hint {
            text = text == null ? "" : text;
        }
    }

    /**
     * 本轮流式调用正常结束。对应 SSE {@code done}；同步 {@code /invoke} 也从这里取最终正文和用量。
     *
     * <p>字段约定：
     * <ul>
     *   <li>{@code finalOutput}：模型最终文本；{@code null} 收成 {@code ""}。</li>
     *   <li>{@code inputTokens} / {@code outputTokens}：本轮累计用量（多轮 ReAct 会加总）；负数收成 0。
     *       由 codec 从 {@code ModelCallEndEvent} / {@code Msg#getChatUsage()} 累加后写入，不是单次模型调用。</li>
     *   <li>{@code data}：结构化输出 Map。{@code null} 或空 Map 都存成 {@code null}（SSE 省略 {@code data} 键）。
     *       非空时经 {@link AgentInvokeResult#copyData} 做成不可变副本，避免调用方改到内部状态。</li>
     *   <li>{@code planActive}：结束时 Plan Mode 是否仍激活。codec <em>不</em>填这个字段（默认 {@code false}）；
     *       {@code ScopeChatAgent#withPlan} 在发出前按 {@code RuntimeContext} 改写。</li>
     * </ul>
     *
     * <p>短构造给测试和不带 structured / plan 的路径用，一律走到上面的规范化。
     */
    record Done(String finalOutput, int inputTokens, int outputTokens, Map<String, Object> data, boolean planActive)
            implements AgentEvent {
        /** 仅正文；用量 0、无 structured、plan 未激活。 */
        public Done(String finalOutput) {
            this(finalOutput, 0, 0, null, false);
        }

        /** 正文 + 累计 token。 */
        public Done(String finalOutput, int inputTokens, int outputTokens) {
            this(finalOutput, inputTokens, outputTokens, null, false);
        }

        /** 正文 + token + 结构化 Map；{@code planActive=false}。 */
        public Done(String finalOutput, int inputTokens, int outputTokens, Map<String, Object> data) {
            this(finalOutput, inputTokens, outputTokens, data, false);
        }

        public Done {
            finalOutput = finalOutput == null ? "" : finalOutput;
            if (inputTokens < 0) {
                inputTokens = 0;
            }
            if (outputTokens < 0) {
                outputTokens = 0;
            }
            data = AgentInvokeResult.copyData(data);
        }
    }

    /**
     * 可对前端说的失败。对应 SSE {@code error}。
     *
     * <p>目前 codec 只把「超过最大迭代」编成本类型。订阅级异常（超时、模型失败）走
     * {@link AgentStreamHandler#onError}，web 会再造一条 {@code Error} 推给客户端后结束流。
     *
     * @param message 人类可读原因，例如 {@code exceeded max iterations: 10/10}
     */
    record Error(String message) implements AgentEvent {}
}
