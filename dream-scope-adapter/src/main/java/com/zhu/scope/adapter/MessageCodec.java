package com.zhu.scope.adapter;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;

/**
 * domain 字符串 ↔ AgentScope 消息。框架类型不得漏到 web / domain。
 */
public final class MessageCodec {

    private MessageCodec() {}

    public static Msg toUserMessage(String input) {
        return new UserMessage(input == null ? "" : input);
    }

    public static String textOf(Msg msg) {
        if (msg == null) {
            return "";
        }
        String text = msg.getTextContent();
        return text == null ? "" : text;
    }
}
