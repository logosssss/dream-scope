package com.zhu.scope.boot.agent.event;

import com.zhu.scope.boot.agent.message.StarterMessages;
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
 * 官方 starter 事件流 → {@link StarterEvent}。一次订阅一个实例，不跨请求复用。
 */
public final class StarterEventCodec {

    private final Map<String, String> toolNames = new HashMap<>();

    private final Map<String, StringBuilder> toolInputs = new HashMap<>();

    private final Map<String, StringBuilder> toolOutputs = new HashMap<>();

    private int inputTokens;

    private int outputTokens;

    public Optional<StarterEvent> toEvent(io.agentscope.core.event.AgentEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        return switch (event) {
            case TextBlockDeltaEvent e -> emitText(e.getDelta(), StarterEvent.TextDelta::new);
            case ToolCallStartEvent e -> rememberTool(e.getToolCallId(), e.getToolCallName());
            case ToolCallDeltaEvent e ->
                    bufferTool(toolInputs, e.getToolCallId(), e.getToolCallName(), e.getDelta());
            case ToolCallEndEvent e -> Optional.of(new StarterEvent.ToolCall(
                    finishTool(e.getToolCallId(), e.getToolCallName()), take(toolInputs, e.getToolCallId())));
            case ToolResultTextDeltaEvent e ->
                    bufferTool(toolOutputs, e.getToolCallId(), e.getToolCallName(), e.getDelta());
            case ToolResultEndEvent e -> Optional.of(new StarterEvent.ToolResult(
                    finishTool(e.getToolCallId(), e.getToolCallName()), take(toolOutputs, e.getToolCallId())));
            case HintBlockEvent e -> emitText(e.getHint(), StarterEvent.Hint::new);
            case ModelCallEndEvent e -> {
                accumulateUsage(e.getUsage());
                yield Optional.empty();
            }
            case AgentResultEvent e -> Optional.of(doneOf(e.getResult()));
            case ExceedMaxItersEvent e -> Optional.of(new StarterEvent.Error(
                    "exceeded max iterations: " + e.getCurrentIter() + "/" + e.getMaxIters()));
            default -> Optional.empty();
        };
    }

    private StarterEvent.Done doneOf(Msg msg) {
        return new StarterEvent.Done(
                StarterMessages.textOf(msg), inputTokens, outputTokens, StarterMessages.structuredOf(msg));
    }

    private static Optional<StarterEvent> emitText(String text, Function<String, StarterEvent> factory) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(factory.apply(text));
    }

    private Optional<StarterEvent> rememberTool(String toolCallId, String toolName) {
        rememberName(toolCallId, toolName);
        return Optional.empty();
    }

    private Optional<StarterEvent> bufferTool(
            Map<String, StringBuilder> buffers, String toolCallId, String toolName, String delta) {
        rememberName(toolCallId, toolName);
        append(buffers, toolCallId, delta);
        return Optional.empty();
    }

    private String finishTool(String toolCallId, String toolName) {
        rememberName(toolCallId, toolName);
        return nameOf(toolCallId, toolName);
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
