package com.zhu.scope.adapter.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zhu.scope.adapter.A2aSupport;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import io.a2a.spec.AgentCard;
import io.a2a.spec.JSONRPCResponse;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.Test;

class ScopeA2aServerTest {

    @Test
    void agentCardAndMessageSendGoThroughOfficialServer() throws Exception {
        ScopeA2aServer server = ScopeA2aServer.create(new AgentHandler() {
            @Override
            public String id() {
                return "chat";
            }

            @Override
            public AgentInvokeResult handle(AgentInvokeRequest request) {
                return new AgentInvokeResult("chat", "stub:" + request.input());
            }
        }, "http://127.0.0.1:8091");
        Object cardObj = server.agentCard();
        assertNotNull(cardObj);
        AgentCard card = (AgentCard) cardObj;
        assertEquals("dream-scope-chat", card.name());
        assertEquals("http://127.0.0.1:8091/a2a", card.url());

        Object raw = server.handleJsonRpc(A2aSupport.requestJson("你好"), java.util.Map.of());
        assertNotNull(raw);
        JSONRPCResponse<?> rpc = (JSONRPCResponse<?>) raw;
        assertEquals("1", String.valueOf(rpc.getId()));
        Message message = (Message) rpc.getResult();
        assertEquals("stub:你好", ((TextPart) message.getParts().get(0)).getText());
    }
}
