package com.zhu.scope.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zhu.scope.adapter.a2a.ScopeA2aServer;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ScopeA2aClientAgentTest {

    @Test
    void handleUsesOfficialA2aAgentAgainstOfficialServer() throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = http.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;
        ScopeA2aServer server = ScopeA2aServer.create(new AgentHandler() {
            @Override
            public String id() {
                return "chat";
            }

            @Override
            public AgentInvokeResult handle(AgentInvokeRequest request) {
                return new AgentInvokeResult("chat", "echo:" + request.input());
            }
        }, base);
        ObjectMapper mapper = new ObjectMapper();
        http.createContext("/.well-known/agent-card.json", exchange -> {
            byte[] out = mapper.writeValueAsBytes(server.agentCard());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        http.createContext("/a2a", exchange -> {
            String req = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Object resp = server.handleJsonRpc(req, java.util.Map.of());
            byte[] out = mapper.writeValueAsBytes(resp);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        http.start();
        try {
            ScopeA2aClientAgent agent = new ScopeA2aClientAgent(base);
            AgentInvokeResult result = agent.handle(new AgentInvokeRequest(AgentIds.A2A, "s", "u", "你好"));
            assertEquals(AgentIds.A2A, result.agentId());
            assertEquals("echo:你好", result.output());
        } finally {
            http.stop(0);
        }
    }

    @Test
    void normalizeBaseStripsA2aPath() {
        assertEquals("http://h", ScopeA2aClientAgent.normalizeBase("http://h"));
        assertEquals("http://h", ScopeA2aClientAgent.normalizeBase("http://h/a2a/"));
    }
}
