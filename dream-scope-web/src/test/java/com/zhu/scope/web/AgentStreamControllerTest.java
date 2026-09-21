package com.zhu.scope.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhu.scope.agent.AgentEvent;
import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentIds;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentRegistry;
import com.zhu.scope.agent.AgentStreamHandler;
import com.zhu.scope.agent.StreamingAgentHandler;
import java.util.Optional;

import com.zhu.scope.web.config.DreamScopeProperties;
import com.zhu.scope.web.controller.AgentInvokeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = AgentInvokeController.class)
@EnableConfigurationProperties(DreamScopeProperties.class)
class AgentStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentRegistry registry;

    @Test
    void streamEmitsTextDeltaThenDone() throws Exception {
        org.mockito.Mockito.when(registry.find("chat")).thenReturn(Optional.of(new StubStreamingAgent()));

        MvcResult mvcResult = mockMvc.perform(post("/api/agents/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"input\":\"hello\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("textDelta")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hello")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hint")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("done")));
    }

    @Test
    void blankInputIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/agents/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAgentIsNotFound() throws Exception {
        org.mockito.Mockito.when(registry.find("nope")).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/agents/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"nope\",\"input\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonStreamingAgentIsNotImplemented() throws Exception {
        org.mockito.Mockito.when(registry.find("chat")).thenReturn(Optional.of(new AgentHandler() {
            @Override
            public String id() {
                return AgentIds.CHAT;
            }

            @Override
            public AgentInvokeResult handle(AgentInvokeRequest request) {
                return new AgentInvokeResult(id(), "no-stream");
            }
        }));
        mockMvc.perform(post("/api/agents/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"hello\"}"))
                .andExpect(status().isNotImplemented());
    }

    private static final class StubStreamingAgent implements StreamingAgentHandler {
        @Override
        public String id() {
            return AgentIds.CHAT;
        }

        @Override
        public AgentInvokeResult handle(AgentInvokeRequest request) {
            return new AgentInvokeResult(id(), request.input());
        }

        @Override
        public void streamHandle(AgentInvokeRequest request, AgentStreamHandler handler) {
            Thread starter = new Thread(
                    () -> {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        handler.onEvent(new AgentEvent.TextDelta(request.input()));
                        handler.onEvent(new AgentEvent.Hint("先列步骤"));
                        handler.onEvent(new AgentEvent.Done(request.input()));
                        handler.onComplete();
                    },
                    "sse-stub");
            starter.start();
        }
    }
}
