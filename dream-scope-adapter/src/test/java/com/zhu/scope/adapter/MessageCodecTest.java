package com.zhu.scope.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

class MessageCodecTest {

    @Test
    void userTextRoundTrip() {
        Msg msg = MessageCodec.toUserMessage("你好");
        assertEquals(MsgRole.USER, msg.getRole());
        assertEquals("你好", MessageCodec.textOf(msg));
    }

    @Test
    void nullInputBecomesEmpty() {
        Msg msg = MessageCodec.toUserMessage(null);
        assertTrue(MessageCodec.textOf(msg).isEmpty());
        assertEquals("", MessageCodec.textOf(null));
    }
}
