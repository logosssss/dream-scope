package com.zhu.scope.boot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhu.scope.agent.AgentEvent;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentProviderException;
import com.zhu.scope.agent.AgentStreamHandler;
import com.zhu.scope.agent.AgentTimeoutException;
import com.zhu.scope.agent.StreamingAgentHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "agentscope.agent.enabled=false",
            "agentscope.dashscope.enabled=false",
            "agentscope.a2a.server.enabled=false",
            "agentscope.nacos.prompt.enabled=false",
            "agentscope.admin.enabled=false"
        })
class StarterInvokeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestBean(name = "starterChatAgent")
    AgentHandler starterChatAgent;

    static AgentHandler starterChatAgent() {
        return new StreamingAgentHandler() {
            @Override
            public String id() {
                return AgentIds.CHAT;
            }

            @Override
            public AgentInvokeResult handle(AgentInvokeRequest request) {
                if ("timeout".equals(request.input())) {
                    throw new AgentTimeoutException("timed out", null);
                }
                if ("fail".equals(request.input())) {
                    throw new AgentProviderException("upstream", null);
                }
                return new AgentInvokeResult(id(), "stub:" + request.input());
            }

            @Override
            public void streamHandle(AgentInvokeRequest request, AgentStreamHandler handler) {
                handler.onEvent(new AgentEvent.TextDelta("stub:" + request.input()));
                handler.onEvent(new AgentEvent.Done("stub:" + request.input()));
                handler.onComplete();
            }
        };
    }

    @Test
    void chatInvokeReturnsHandlerOutput() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("chat"))
                .andExpect(jsonPath("$.output").value("stub:你好"))
                .andExpect(jsonPath("$.planActive").value(false));
    }

    @Test
    void blankInputIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modelTimeoutIsGatewayTimeout() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"timeout\"}"))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void modelFailureIsBadGateway() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"fail\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void unknownAgentIsNotFound() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"nope\",\"input\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}
