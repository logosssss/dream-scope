package com.zhu.scope.adapter.event;

import com.zhu.scope.adapter.MessageCodec;
import com.zhu.scope.agent.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.model.ChatUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AgentScope {@link io.agentscope.core.event.AgentEvent} → domain {@link AgentEvent}。
 *
 * <p>一次订阅对应一个实例。工具入参/出参在 2.0.3 里拆成 delta + end，end 事件没有完整 input/output getter，
 * 因此按 {@code toolCallId} 累积后再在 end 上弹出。{@link ModelCallEndEvent} 的用量累加后挂到 {@link AgentEvent.Done}。
 */
public final class EventCodec {

    private final Map<String, String> toolNames = new HashMap<>();

    private final Map<String, StringBuilder> toolInputs = new HashMap<>();

    private final Map<String, StringBuilder> toolOutputs = new HashMap<>();

    private int inputTokens;

    private int outputTokens;

    public Optional<AgentEvent> toDomain(io.agentscope.core.event.AgentEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        if (event instanceof TextBlockDeltaEvent delta) {
            String text = delta.getDelta();
            if (text == null || text.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new AgentEvent.TextDelta(text));
        }
        if (event instanceof ToolCallStartEvent start) {
            rememberName(start.getToolCallId(), start.getToolCallName());
            return Optional.empty();
        }
        if (event instanceof ToolCallDeltaEvent delta) {
            rememberName(delta.getToolCallId(), delta.getToolCallName());
            append(toolInputs, delta.getToolCallId(), delta.getDelta());
            return Optional.empty();
        }
        if (event instanceof ToolCallEndEvent end) {
            rememberName(end.getToolCallId(), end.getToolCallName());
            String name = nameOf(end.getToolCallId(), end.getToolCallName());
            String input = take(toolInputs, end.getToolCallId());
            return Optional.of(new AgentEvent.ToolCall(name, input));
        }
        if (event instanceof ToolResultTextDeltaEvent delta) {
            rememberName(delta.getToolCallId(), delta.getToolCallName());
            append(toolOutputs, delta.getToolCallId(), delta.getDelta());
            return Optional.empty();
        }
        if (event instanceof ToolResultEndEvent end) {
            rememberName(end.getToolCallId(), end.getToolCallName());
            String name = nameOf(end.getToolCallId(), end.getToolCallName());
            String output = take(toolOutputs, end.getToolCallId());
            return Optional.of(new AgentEvent.ToolResult(name, output));
        }
        if (event instanceof ModelCallEndEvent end) {
            accumulateUsage(end.getUsage());
            return Optional.empty();
        }
        if (event instanceof AgentResultEvent result) {
            return Optional.of(new AgentEvent.Done(MessageCodec.textOf(result.getResult()), inputTokens, outputTokens));
        }
        if (event instanceof ExceedMaxItersEvent exceed) {
            return Optional.of(new AgentEvent.Error(
                    "exceeded max iterations: " + exceed.getCurrentIter() + "/" + exceed.getMaxIters()));
        }
        return Optional.empty();
    }

    private void accumulateUsage(ChatUsage usage) {
        if (usage == null) {
            return;
        }
        inputTokens += Math.max(0, usage.getInputTokens());
        outputTokens += Math.max(0, usage.getOutputTokens());
    }

    private void rememberName(String toolCallId, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        toolNames.put(key(toolCallId), toolName);
    }

    private String nameOf(String toolCallId, String toolName) {
        if (toolName != null && !toolName.isBlank()) {
            return toolName;
        }
        String remembered = toolNames.get(key(toolCallId));
        return remembered == null ? "" : remembered;
    }

    private static void append(Map<String, StringBuilder> buffers, String toolCallId, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        buffers.computeIfAbsent(key(toolCallId), ignored -> new StringBuilder()).append(delta);
    }

    private static String take(Map<String, StringBuilder> buffers, String toolCallId) {
        StringBuilder buffer = buffers.remove(key(toolCallId));
        return buffer == null ? "" : buffer.toString();
    }

    private static String key(String toolCallId) {
        return toolCallId == null || toolCallId.isBlank() ? "_" : toolCallId;
    }
}
