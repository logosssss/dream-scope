package com.zhu.scope.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import java.util.List;
import java.util.Map;
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

    @Test
    void imageUrlsBecomeImageBlocks() {
        Msg msg = MessageCodec.toUserMessage("看图", List.of("https://example.com/a.png"));
        assertEquals("看图", MessageCodec.textOf(msg));
        assertEquals(1, msg.getContentBlocks(TextBlock.class).size());
        List<ImageBlock> images = msg.getContentBlocks(ImageBlock.class);
        assertEquals(1, images.size());
        assertInstanceOf(URLSource.class, images.getFirst().getSource());
        assertEquals("https://example.com/a.png", ((URLSource) images.getFirst().getSource()).getUrl());
    }

    @Test
    void structuredOfReadsMap() {
        Msg msg = org.mockito.Mockito.mock(Msg.class);
        org.mockito.Mockito.when(msg.hasStructuredData()).thenReturn(true);
        org.mockito.Mockito.when(msg.getStructuredData(true)).thenReturn(Map.of("city", "大阪"));
        assertEquals("大阪", MessageCodec.structuredOf(msg).get("city"));
    }
}
