package com.zhu.scope.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhu.scope.adapter.A2aSupport;
import com.zhu.scope.adapter.a2a.ScopeA2aServer;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = A2aController.class)
@EnableConfigurationProperties(DreamScopeProperties.class)
@Import(A2aControllerTest.A2aTestConfig.class)
class A2aControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void agentCardIsExposed() throws Exception {
        mockMvc.perform(get("/.well-known/agent-card.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("dream-scope-chat"))
                .andExpect(jsonPath("$.url").value("http://127.0.0.1:8091/a2a"));
    }

    @Test
    void messageSendDelegatesToChat() throws Exception {
        mockMvc.perform(post("/a2a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(A2aSupport.requestJson("你好")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.parts[0].text").value("stub:你好"));
    }

    @TestConfiguration
    static class A2aTestConfig {
        @Bean
        ScopeA2aServer scopeA2aServer() {
            return ScopeA2aServer.create(new AgentHandler() {
                @Override
                public String id() {
                    return "chat";
                }

                @Override
                public AgentInvokeResult handle(AgentInvokeRequest request) {
                    return new AgentInvokeResult("chat", "stub:" + request.input());
                }
            }, "http://127.0.0.1:8091");
        }
    }
}
