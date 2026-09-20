package com.zhu.scope.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "dream-scope.rag.provider=keyword")
@AutoConfigureMockMvc
class NacosCloudControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestBean(name = "chatAgentHandler")
    AgentHandler chatAgentHandler;

    static AgentHandler chatAgentHandler() {
        return new AgentHandler() {
            @Override
            public String id() {
                return AgentIds.CHAT;
            }

            @Override
            public AgentInvokeResult handle(AgentInvokeRequest request) {
                return new AgentInvokeResult(id(), "stub");
            }
        };
    }

    @Test
    void statusIsOffByDefault() throws Exception {
        mockMvc.perform(get("/api/nacos/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("dream-scope"))
                .andExpect(jsonPath("$.configEnabled").value(false))
                .andExpect(jsonPath("$.discoveryEnabled").value(false))
                .andExpect(jsonPath("$.aiEnabled").value(false))
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.services.length()").value(0));
    }

    @Test
    void instancesEmptyWhenDiscoveryOff() throws Exception {
        mockMvc.perform(get("/api/nacos/instances/dream-scope"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
