package com.zhu.scope.web;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhu.scope.knowledge.KnowledgeIngestTracker;
import com.zhu.scope.knowledge.KnowledgeSource;
import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.web.controller.KnowledgeController;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = KnowledgeController.class)
@Import(KnowledgeControllerTest.TrackerBeans.class)
class KnowledgeControllerTest {

    @TestConfiguration
    static class TrackerBeans {
        @Bean
        KnowledgeIngestTracker knowledgeIngestTracker() {
            return new KnowledgeIngestTracker();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RetrievePort retrievePort;

    @Test
    void addTextReturnsId() throws Exception {
        when(retrievePort.addText("doc-1", "dream-scope 运行时", "manual", "note")).thenReturn("doc-1");
        mockMvc.perform(post("/api/knowledge/texts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"id\":\"doc-1\",\"text\":\"dream-scope 运行时\",\"source\":\"manual\",\"docType\":\"note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.source").value("manual"))
                .andExpect(jsonPath("$.docType").value("note"));
    }

    @Test
    void blankTextIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/knowledge/texts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestFailureIsBadGateway() throws Exception {
        when(retrievePort.addText(null, "hello", "", ""))
                .thenThrow(new IllegalStateException("simple knowledge ingest failed"));
        mockMvc.perform(post("/api/knowledge/texts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void addFileUsesMultipartAndExposesJob() throws Exception {
        when(retrievePort.addFile(
                        null,
                        "note.md",
                        "dream-scope 运行时".getBytes(StandardCharsets.UTF_8),
                        "note.md",
                        "file"))
                .thenReturn(List.of("note"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.md", "text/markdown", "dream-scope 运行时".getBytes(StandardCharsets.UTF_8));
        MvcResult accepted = mockMvc.perform(multipart("/api/knowledge/files").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isString())
                .andExpect(jsonPath("$.filename").value("note.md"))
                .andExpect(jsonPath("$.source").value("note.md"))
                .andExpect(jsonPath("$.docType").value("file"))
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.ids").isEmpty())
                .andReturn();
        String jobId = com.jayway.jsonpath.JsonPath.read(accepted.getResponse().getContentAsString(), "$.jobId");
        verify(retrievePort, timeout(3000))
                .addFile(
                        null,
                        "note.md",
                        "dream-scope 运行时".getBytes(StandardCharsets.UTF_8),
                        "note.md",
                        "file");
        awaitJobStatus(jobId, "done");
        mockMvc.perform(get("/api/knowledge/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.ids[0]").value("note"));
    }

    @Test
    void addFileKeepsHttpAcceptedWhenIngestFails() throws Exception {
        when(retrievePort.addFile(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("scan.pdf"),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("image ingest needs multimodal embedding"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "scan.pdf", "application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46});
        MvcResult accepted = mockMvc.perform(multipart("/api/knowledge/files").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.jobId").isString())
                .andReturn();
        String jobId = com.jayway.jsonpath.JsonPath.read(accepted.getResponse().getContentAsString(), "$.jobId");
        verify(retrievePort, timeout(3000))
                .addFile(
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq("scan.pdf"),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("scan.pdf"),
                        org.mockito.ArgumentMatchers.eq("file"));
        awaitJobStatus(jobId, "failed");
        mockMvc.perform(get("/api/knowledge/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    @Test
    void addFileRequiresFilename() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/knowledge/files").file(file)).andExpect(status().isBadRequest());
    }

    @Test
    void retrieveReturnsHits() throws Exception {
        when(retrievePort.retrieve("Redis", 2))
                .thenReturn(List.of(new RetrieveHit("h1", "Redis 会话", 0.9, "demo", "note")));
        mockMvc.perform(post("/api/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Redis\",\"topK\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("Redis"))
                .andExpect(jsonPath("$.topK").value(2))
                .andExpect(jsonPath("$.hits[0].id").value("h1"))
                .andExpect(jsonPath("$.hits[0].score").value(0.9));
    }

    @Test
    void blankRetrieveQueryIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsAndDeletesSources() throws Exception {
        when(retrievePort.sources()).thenReturn(List.of(new KnowledgeSource("guide.pdf", 4)));
        when(retrievePort.deleteBySource("guide.pdf")).thenReturn(4);
        mockMvc.perform(get("/api/knowledge/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("guide.pdf"))
                .andExpect(jsonPath("$[0].chunks").value(4));
        mockMvc.perform(delete("/api/knowledge/sources").param("source", "guide.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("guide.pdf"))
                .andExpect(jsonPath("$.deleted").value(4));
    }

    @Test
    void retrieveCanLimitToOneSource() throws Exception {
        when(retrievePort.retrieve("Redis", 2, "guide.pdf"))
                .thenReturn(List.of(new RetrieveHit("h1", "Redis 会话", 0.8, "guide.pdf", "file")));
        mockMvc.perform(post("/api/knowledge/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Redis\",\"topK\":2,\"source\":\"guide.pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].source").value("guide.pdf"));
    }

    private void awaitJobStatus(String jobId, String expected) throws Exception {
        AssertionError last = null;
        for (int i = 0; i < 40; i++) {
            try {
                mockMvc.perform(get("/api/knowledge/jobs/" + jobId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value(expected));
                return;
            } catch (AssertionError ex) {
                last = ex;
                Thread.sleep(50L);
            }
        }
        throw last == null ? new AssertionError("job status timeout") : last;
    }
}
