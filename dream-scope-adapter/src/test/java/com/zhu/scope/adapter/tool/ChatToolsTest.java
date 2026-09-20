package com.zhu.scope.adapter.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.zhu.scope.knowledge.RetrieveHit;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatToolsTest {

    private final ChatTools tools = new ChatTools();

    @Test
    void getCurrentTimeReturnsIsoOffset() {
        String text = tools.getCurrentTime();
        OffsetDateTime.parse(text);
        assertTrue(text.contains("T"));
    }

    @Test
    void calculateEvaluatesArithmetic() {
        assertEquals("7", tools.calculate("1+2*3"));
        assertEquals("9", tools.calculate("(1+2)*3"));
        assertEquals("无法计算: 除数为 0", tools.calculate("1/0"));
        assertTrue(tools.calculate("not-a-number").startsWith("无法计算:"));
    }

    @Test
    void httpGetReturnsClippedBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] payload = "hello-world-payload".getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            String body = tools.httpGet(url, 5);
            assertTrue(body.startsWith("status=200"));
            assertTrue(body.contains("hello"));
            assertTrue(body.endsWith("hello"));
        } finally {
            server.stop(0);
        }
        assertEquals("仅支持 http/https", tools.httpGet("ftp://example.com", 10));
    }

    @Test
    void retrieveFormatsHitsAndCapsTopK() {
        ChatTools wired = new ChatTools((query, topK) -> {
            assertEquals(3, topK);
            if (query.contains("dream")) {
                return List.of(new RetrieveHit("kw-1", "dream-scope 是运行时", 1.0, "intro", "intro"));
            }
            return List.of();
        });
        assertEquals("[1] dream-scope 是运行时", wired.retrieve("dream-scope", null));
        assertEquals("未检索到相关资料", wired.retrieve("无关问题", 3));
    }

    @Test
    void retrieveWithoutIndexExplainsMissingStore() {
        assertEquals("知识库未配置", new ChatTools().retrieve("dream-scope", 3));
    }
}
