package com.zhu.scope.boot.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhu.scope.boot.agent.event.StarterEvent;
import com.zhu.scope.boot.agent.StarterHandler;
import com.zhu.scope.boot.agent.excption.StarterProviderException;
import com.zhu.scope.boot.agent.bean.request.StarterRequest;
import com.zhu.scope.boot.agent.bean.response.StarterResult;
import com.zhu.scope.boot.agent.excption.StarterTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
            "agentscope.admin.enabled=false",
            "dream-scope.mcp.demo-enabled=false",
            "dream-scope.knowledge.api-key=",
            "dream-scope.knowledge.pg.jdbc-url="
        })
class StarterInvokeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestBean(name = "starterChatAgent")
    StarterHandler starterChatAgent;

    static StarterHandler starterChatAgent() {
        return new StarterHandler() {
            @Override
            public String id() {
                return CHAT;
            }

            @Override
            public StarterResult handle(StarterRequest request) {
                if ("timeout".equals(request.input())) {
                    throw new StarterTimeoutException("timed out", null);
                }
                if ("fail".equals(request.input())) {
                    throw new StarterProviderException("upstream", null);
                }
                return new StarterResult(id(), "stub:" + request.input());
            }

            @Override
            public void streamHandle(StarterRequest request, Sink sink) {
                sink.onEvent(new StarterEvent.TextDelta("stub:" + request.input()));
                sink.onEvent(new StarterEvent.Done("stub:" + request.input()));
                sink.onComplete();
            }
        };
    }

    @Test
    void catalogListsOfficialStarters() throws Exception {
        mockMvc.perform(get("/api/starters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("dashscope"))
                .andExpect(jsonPath("$.fallback").value(""))
                .andExpect(jsonPath("$.generate.temperature").value(nullValue()))
                .andExpect(jsonPath("$.generate.topP").value(nullValue()))
                .andExpect(jsonPath("$.generate.maxTokens").value(nullValue()))
                .andExpect(jsonPath("$.plan.enabled").value(true))
                .andExpect(jsonPath("$.plan.maxIters").value(10))
                .andExpect(jsonPath("$.plan.directory").value("plans"))
                .andExpect(jsonPath("$.starters.length()").value(11))
                .andExpect(jsonPath("$.starters[?(@.id=='agentscope-chat-completions-web-starter')].entry")
                        .value(hasItem("POST /v1/chat/completions")))
                .andExpect(jsonPath("$.starters[?(@.id=='agentscope-nacos-spring-boot-starter')].enabled")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.mcp.enabled").value(false))
                .andExpect(jsonPath("$.mcp.tool").value("mcp__boot__echo"))
                .andExpect(jsonPath("$.knowledge.agentId").value("knowledge"))
                .andExpect(jsonPath("$.knowledge.guide").value("knowledge/KNOWLEDGE.md"))
                .andExpect(jsonPath("$.knowledge.mode").value("keyword"))
                .andExpect(jsonPath("$.knowledge.store").value("memory"))
                .andExpect(jsonPath("$.transcript.enabled").value(true))
                .andExpect(jsonPath("$.transcript.directory").value("transcripts"))
                .andExpect(jsonPath("$.eviction.enabled").value(true))
                .andExpect(jsonPath("$.eviction.maxResultChars").value(80000))
                .andExpect(jsonPath("$.eviction.previewChars").value(2000))
                .andExpect(jsonPath("$.eviction.path").value("large_tool_results"))
                .andExpect(jsonPath("$.memory.enabled").value(true))
                .andExpect(jsonPath("$.memory.file").value("MEMORY.md"))
                .andExpect(jsonPath("$.session.store").value("redis"))
                .andExpect(jsonPath("$.session.keyPrefix").value("dream-scope-boot:"))
                .andExpect(jsonPath("$.session.compactionTrigger").value(30))
                .andExpect(jsonPath("$.session.compactionKeep").value(10))
                .andExpect(jsonPath("$.tools.file").value("tools.json"))
                .andExpect(jsonPath("$.tools.read[0]").value("read_file"))
                .andExpect(jsonPath("$.tools.deny[0]").value("web_fetch"))
                .andExpect(jsonPath("$.tools.deny[1]").value("web_search"))
                .andExpect(jsonPath("$.tools.deny[2]").value("write_file"))
                .andExpect(jsonPath("$.tools.deny[3]").value("edit_file"))
                .andExpect(jsonPath("$.agents.file").value("AGENTS.md"))
                .andExpect(jsonPath("$.agents.seed").value(true))
                .andExpect(jsonPath("$.skills.directory").value("skills"))
                .andExpect(jsonPath("$.skills.demo").value("boot-echo"))
                .andExpect(jsonPath("$.subagents.demo").value("summarizer"))
                .andExpect(jsonPath("$.nacosSkill.enabled").value(false))
                .andExpect(jsonPath("$.nacosSkill.names.length()").value(0))
                .andExpect(jsonPath("$.a2aClient.enabled").value(false))
                .andExpect(jsonPath("$.a2aClient.name").value("remote"))
                .andExpect(jsonPath("$.a2aClient.entry").value("POST /api/a2a/invoke"));
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
    void knowledgeTextCanBeRetrieved() throws Exception {
        mockMvc.perform(post("/api/knowledge/texts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"note-1\",\"text\":\"量子隧道演示条目\",\"source\":\"lab\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("note-1"))
                .andExpect(jsonPath("$.source").value("lab"));
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"knowledge\",\"input\":\"量子隧道\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value(containsString("量子隧道演示条目")));
    }

    @Test
    void knowledgeFileIsParsedAndRetrieved() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", "量子文件条目".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/knowledge/files").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("note.txt"))
                .andExpect(jsonPath("$.ids[0]").value("note"));
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"knowledge\",\"input\":\"量子文件\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value(containsString("量子文件条目")));
    }

    @Test
    void knowledgeFileReuploadReplacesOldChunks() throws Exception {
        MockMultipartFile first = new MockMultipartFile(
                "file", "cover.txt", "text/plain", "甲乙丙丁旧稿".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/knowledge/files").file(first)).andExpect(status().isOk());
        MockMultipartFile second = new MockMultipartFile(
                "file", "cover.txt", "text/plain", "戊己庚辛新稿".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/knowledge/files").file(second)).andExpect(status().isOk());
        mockMvc.perform(post("/api/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"甲乙丙丁\",\"source\":\"cover.txt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits.length()").value(0));
        mockMvc.perform(post("/api/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"戊己庚辛\",\"source\":\"cover.txt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits.length()").value(1));
    }

    @Test
    void knowledgeRetrieveFiltersBySource() throws Exception {
        mockMvc.perform(post("/api/knowledge/texts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"find-1\",\"text\":\"直接检索专用条目\",\"source\":\"lab-find\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"直接检索\",\"source\":\"lab-find\",\"topK\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].id").value("find-1"))
                .andExpect(jsonPath("$.hits[0].source").value("lab-find"));
        mockMvc.perform(post("/api/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"直接检索\",\"source\":\"missing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits.length()").value(0));
    }

    @Test
    void knowledgeSourceCanBeListedAndDeleted() throws Exception {
        mockMvc.perform(post("/api/knowledge/texts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"src-1\",\"text\":\"来源删除专用条目\",\"source\":\"lab-src\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/knowledge/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[?(@.source=='lab-src')].count").value(hasItem(1)));
        mockMvc.perform(delete("/api/knowledge/sources").param("source", "lab-src"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(1));
        mockMvc.perform(post("/api/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"来源删除\",\"source\":\"lab-src\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits.length()").value(0));
    }

    @Test
    void blankKnowledgeTextIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/knowledge/texts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void knowledgeInvokeReturnsCitation() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"knowledge\",\"input\":\"8092\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value("knowledge"))
                .andExpect(jsonPath("$.output").value(containsString("8092")));
    }

    @Test
    void unknownAgentIsNotFound() throws Exception {
        mockMvc.perform(post("/api/agents/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"nope\",\"input\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}
