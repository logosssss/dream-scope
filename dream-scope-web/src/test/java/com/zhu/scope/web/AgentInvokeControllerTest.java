package com.zhu.scope.web;

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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "dream-scope.rag.provider=keyword")
@AutoConfigureMockMvc
class AgentInvokeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestBean(name = "chatAgentHandler")
    AgentHandler chatAgentHandler;

    static AgentHandler chatAgentHandler() {
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
                if (request.structured()) {
                    return new AgentInvokeResult(id(), "stub:" + request.input(), 0, 0, java.util.Map.of("city", "大阪"));
                }
                return new AgentInvokeResult(id(), "stub:" + request.input());
            }

            @Override
            public void streamHandle(AgentInvokeRequest request, AgentStreamHandler handler) {
                if ("timeout".equals(request.input())) {
                    handler.onError(new AgentTimeoutException("timed out", null));
                    return;
                }
                if ("fail".equals(request.input())) {
                    handler.onError(new AgentProviderException("upstream", null));
                    return;
                }
                handler.onEvent(new AgentEvent.TextDelta("stub:" + request.input()));
                handler.onEvent(new AgentEvent.Hint("先列步骤"));
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
                .andExpect(jsonPath("$.inputTokens").value(0))
                .andExpect(jsonPath("$.outputTokens").value(0))
                .andExpect(jsonPath("$.planActive").value(false));
    }

    @Test
    void structuredInvokeReturnsData() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"天气\",\"structured\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("大阪"));
    }

    @Test
    void imageUrlsWithoutTextIsOk() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrls\":[\"https://example.com/a.png\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("stub:"));
    }

    @Test
    void nonHttpImageUrlIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"看图\",\"imageUrls\":[\"file:///tmp/a.png\"]}"))
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
    void blankInputIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void knowledgeInvokeReturnsSeededCitation() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"knowledge\",\"input\":\"dream-scope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("knowledge"))
                .andExpect(jsonPath("$.output").value(org.hamcrest.Matchers.containsString("[1]")))
                .andExpect(jsonPath("$.output").value(org.hamcrest.Matchers.containsString("dream-scope")));
    }

    @Test
    void unknownAgentIsNotFound() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"nope\",\"input\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}
