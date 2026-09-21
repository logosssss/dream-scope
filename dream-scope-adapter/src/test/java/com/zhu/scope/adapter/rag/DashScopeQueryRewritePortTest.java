package com.zhu.scope.adapter.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DashScopeQueryRewritePortTest {

    @Test
    void parsesAssistantTextLines() {
        String json = "{\"output\":{\"choices\":[{\"message\":{\"content\":["
                + "{\"text\":\"AgentGateway HTTP\\nPOST /api/agents/invoke\"}]}}]}}";
        List<String> lines = DashScopeQueryRewritePort.parseLines(json);
        assertEquals(List.of("AgentGateway HTTP", "POST /api/agents/invoke"), lines);
    }
}
