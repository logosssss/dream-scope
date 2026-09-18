package com.zhu.scope.adapter;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.UserMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * domain 字符串 / 图片 URL ↔ AgentScope 消息。框架类型不得漏到 web / domain。
 */
public final class MessageCodec {

    private MessageCodec() {}

    public static Msg toUserMessage(String input) {
        return toUserMessage(input, List.of());
    }

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
}
