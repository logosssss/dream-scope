package com.zhu.scope.boot.agent.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.UserMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** boot 内的文本 / 图片 URL 与 AgentScope 消息互转。 */
public final class StarterMessages {

    private static final ObjectMapper JSON = new ObjectMapper();

    private StarterMessages() {}

    public static Msg toUserMessage(String input, List<String> imageUrls) {
        String text = input == null ? "" : input;
        if (imageUrls == null || imageUrls.isEmpty()) {
            return new UserMessage(text);
        }
        List<ContentBlock> blocks = new ArrayList<>();
        if (!text.isBlank()) {
            blocks.add(TextBlock.builder().text(text).build());
        }
        for (String url : imageUrls) {
            blocks.add(new ImageBlock(new URLSource(url)));
        }
        return new UserMessage(blocks);
    }

    public static String textOf(Msg msg) {
        if (msg == null) {
            return "";
        }
        String text = msg.getTextContent();
        return text == null ? "" : text;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> structuredOf(Msg msg) {
        if (msg == null || !msg.hasStructuredData()) {
            return null;
        }
        try {
            Map<String, Object> asMap = msg.getStructuredData(true);
            if (asMap != null && !asMap.isEmpty()) {
                return new LinkedHashMap<>(asMap);
            }
        } catch (RuntimeException ignored) {
            // 走 Class 重载
        }
        try {
            Object typed = msg.getStructuredData(Map.class);
            if (typed instanceof Map<?, ?> map && !map.isEmpty()) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    public static JsonNode jsonSchemaNode(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        return JSON.valueToTree(schema);
    }
}
