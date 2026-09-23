package com.zhu.scope.boot.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.nacos.api.ai.AiService;
import com.zhu.scope.boot.plan.impl.BootPlanAgent;
import com.zhu.scope.boot.skill.BootNacosSkills;
import com.zhu.scope.boot.skill.BootSkills;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.middleware.CompactionMiddleware;
import io.agentscope.harness.agent.middleware.ToolResultEvictionMiddleware;
import java.lang.reflect.Field;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.middleware.TranscriptMiddleware;
import io.agentscope.harness.agent.transcript.FilesystemTranscriptStore;
import io.agentscope.core.middleware.MiddlewareBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BootPlanAgentTest {

    @Test
    void registersPlanToolsAndBypassesConfirm() throws Exception {
        Path workspace = Files.createTempDirectory("boot-plan");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            assertTrue(agent.getToolkit().getToolNames().contains("plan_enter"));
            assertTrue(agent.getToolkit().getToolNames().contains("plan_write"));
            assertTrue(agent.getToolkit().getToolNames().contains("plan_exit"));
            assertTrue(agent.getToolkit().getToolNames().contains("agent_spawn"));
            assertTrue(agent.getSubagentAgentManager().hasAgent("summarizer"));
            assertTrue(agent.getToolkit().getToolNames().contains("memory_search"));
            assertTrue(agent.getToolkit().getToolNames().contains("memory_get"));
            assertTrue(agent.getToolkit().getToolNames().contains("memory_save"));
            assertTrue(agent.getToolkit().getToolNames().contains("session_search"));
            assertEquals(PermissionMode.BYPASS, agent.getPermissionMode("user", "session"));
        } finally {
            agent.close();
        }
    }

    @Test
    void compactsByMessageCount() throws Exception {
        Path workspace = Files.createTempDirectory("boot-plan");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans", null, 4, 2);
        try {
            CompactionConfig config = configOf(agent);
            assertEquals(4, config.getTriggerMessages());
            assertEquals(2, config.getKeepMessages());
            assertEquals(0, config.getKeepTokens());
        } finally {
            agent.close();
        }
    }

    @Test
    void transcriptIsStoredUnderWorkspace() throws Exception {
        Path workspace = Files.createTempDirectory("boot-transcript");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            FilesystemTranscriptStore store = transcriptStore(agent);
            Field root = FilesystemTranscriptStore.class.getDeclaredField("root");
            root.setAccessible(true);
            assertEquals(workspace.resolve("transcripts"), root.get(store));
            assertTrue(Files.isDirectory(workspace.resolve("transcripts")));
        } finally {
            agent.close();
        }
    }

    @Test
    void memoryFileIsSeededWhenMissing() throws Exception {
        Path workspace = Files.createTempDirectory("boot-memory");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            String text = Files.readString(workspace.resolve("MEMORY.md"));
            assertTrue(text.contains("memory_save"));
        } finally {
            agent.close();
        }
    }

    @Test
    void existingMemoryFileIsKept() throws Exception {
        Path workspace = Files.createTempDirectory("boot-memory");
        Files.writeString(workspace.resolve("MEMORY.md"), "keep-me");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            assertEquals("keep-me", Files.readString(workspace.resolve("MEMORY.md")));
        } finally {
            agent.close();
        }
    }

    @Test
    void knowledgeDocIsSeededWhenMissing() throws Exception {
        Path workspace = Files.createTempDirectory("boot-knowledge-doc");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            String text = Files.readString(workspace.resolve("knowledge/KNOWLEDGE.md"));
            assertTrue(text.contains("8092"));
        } finally {
            agent.close();
        }
    }

    @Test
    void existingKnowledgeDocIsKept() throws Exception {
        Path workspace = Files.createTempDirectory("boot-knowledge-doc");
        Path file = workspace.resolve("knowledge/KNOWLEDGE.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "keep-me");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            assertEquals("keep-me", Files.readString(file));
        } finally {
            agent.close();
        }
    }

    @Test
    void readOnlyFileToolsStay() throws Exception {
        Path workspace = Files.createTempDirectory("boot-files");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            assertTrue(agent.getToolkit().getToolNames().contains("read_file"));
            assertTrue(agent.getToolkit().getToolNames().contains("list_files"));
            assertTrue(agent.getToolkit().getToolNames().contains("grep_files"));
            assertTrue(agent.getToolkit().getToolNames().contains("glob_files"));
            assertFalse(agent.getToolkit().getToolNames().contains("write_file"));
            assertFalse(agent.getToolkit().getToolNames().contains("edit_file"));
            assertFalse(agent.getToolkit().getToolNames().contains("execute"));
        } finally {
            agent.close();
        }
    }

    @Test
    void webToolsAreDeniedBySeededToolsFile() throws Exception {
        Path workspace = Files.createTempDirectory("boot-tools");
        Toolkit toolkit = new Toolkit();
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), toolkit, workspace, "plans");
        try {
            assertFalse(agent.getToolkit().getToolNames().contains("web_fetch"));
            assertFalse(agent.getToolkit().getToolNames().contains("web_search"));
            assertTrue(agent.getToolkit().getToolNames().contains("plan_enter"));
            assertTrue(Files.readString(workspace.resolve("tools.json")).contains("web_fetch"));
        } finally {
            agent.close();
        }
    }

    @Test
    void existingToolsFileIsKept() throws Exception {
        Path workspace = Files.createTempDirectory("boot-tools");
        Files.writeString(workspace.resolve("tools.json"), "{\"deny\":[]}");
        Toolkit toolkit = new Toolkit();
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), toolkit, workspace, "plans");
        try {
            assertEquals("{\"deny\":[]}", Files.readString(workspace.resolve("tools.json")));
            assertTrue(agent.getToolkit().getToolNames().contains("web_fetch"));
            assertFalse(agent.getToolkit().getToolNames().contains("write_file"));
            assertFalse(agent.getToolkit().getToolNames().contains("edit_file"));
        } finally {
            agent.close();
        }
    }

    @Test
    void agentsFileIsSeededWhenMissing() throws Exception {
        Path workspace = Files.createTempDirectory("boot-agents");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            String text = Files.readString(workspace.resolve("AGENTS.md"));
            assertTrue(text.contains("dream-scope-boot"));
        } finally {
            agent.close();
        }
    }

    @Test
    void existingAgentsFileIsKept() throws Exception {
        Path workspace = Files.createTempDirectory("boot-agents");
        Files.writeString(workspace.resolve("AGENTS.md"), "keep-me");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            assertEquals("keep-me", Files.readString(workspace.resolve("AGENTS.md")));
        } finally {
            agent.close();
        }
    }

    @Test
    void workspaceSkillIsLoaded() throws Exception {
        Path workspace = Files.createTempDirectory("boot-skill");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            boolean found = false;
            for (AgentSkillRepository repo : agent.getSkillRepositories()) {
                if (repo.getAllSkillNames().contains(BootSkills.DEMO)) {
                    found = true;
                }
            }
            assertTrue(found);
        } finally {
            agent.close();
        }
    }

    @Test
    void fallbackModelIsAttachedWhenNamesDiffer() throws Exception {
        Path workspace = Files.createTempDirectory("boot-fallback");
        Model primary = mock(Model.class);
        Model fallback = mock(Model.class);
        when(primary.getModelName()).thenReturn("qwen-plus");
        when(fallback.getModelName()).thenReturn("qwen-turbo");
        HarnessAgent agent = BootPlanAgent.create(
                primary, new Toolkit(), workspace, "plans", null, 30, 10, "skills", fallback);
        try {
            assertSame(fallback, agent.getDelegate().getModelConfig().fallbackModel());
        } finally {
            agent.close();
        }
    }

    @Test
    void sameNameFallbackIsIgnored() throws Exception {
        Path workspace = Files.createTempDirectory("boot-fallback");
        Model primary = mock(Model.class);
        Model fallback = mock(Model.class);
        when(primary.getModelName()).thenReturn("qwen-plus");
        when(fallback.getModelName()).thenReturn("qwen-plus");
        HarnessAgent agent = BootPlanAgent.create(
                primary, new Toolkit(), workspace, "plans", null, 30, 10, "skills", fallback);
        try {
            assertNull(agent.getDelegate().getModelConfig().fallbackModel());
        } finally {
            agent.close();
        }
    }

    @Test
    void toolResultEvictionKeepsConfiguredLimits() throws Exception {
        Path workspace = Files.createTempDirectory("boot-evict");
        HarnessAgent agent = BootPlanAgent.create(
                mock(Model.class), new Toolkit(), workspace, "plans", null, 30, 10, "skills", null, 120, 30, "spills");
        try {
            ToolResultEvictionConfig config = evictionOf(agent);
            assertEquals(120, config.getMaxResultChars());
            assertEquals(30, config.getPreviewChars());
            assertEquals("spills", config.getEvictionPath());
            assertTrue(config.getExcludedToolNames().contains("read_file"));
        } finally {
            agent.close();
        }
    }

    @Test
    void nacosSkillRepositoryStaysBesideWorkspaceSkills() throws Exception {
        Path workspace = Files.createTempDirectory("boot-nacos-skill");
        NacosSkillRepository repo = BootNacosSkills.repository(mock(AiService.class), "public", List.of("boot-echo"));
        HarnessAgent agent = BootPlanAgent.create(
                mock(Model.class),
                new Toolkit(),
                workspace,
                "plans",
                null,
                30,
                10,
                "skills",
                null,
                80000,
                2000,
                "large_tool_results",
                null,
                10,
                repo);
        try {
            assertTrue(agent.getSkillRepositories().contains(repo));
            assertEquals(List.of("boot-echo"), repo.getAllSkillNames());
        } finally {
            agent.close();
        }
    }

    @Test
    void maxItersCapsTheLoop() throws Exception {
        Path workspace = Files.createTempDirectory("boot-iters");
        HarnessAgent agent = BootPlanAgent.create(
                mock(Model.class),
                new Toolkit(),
                workspace,
                "plans",
                null,
                30,
                10,
                "skills",
                null,
                80000,
                2000,
                "large_tool_results",
                null,
                3);
        try {
            assertEquals(3, agent.getDelegate().getMaxIters());
        } finally {
            agent.close();
        }
    }

    @Test
    void nonPositiveMaxItersFallsBack() {
        assertEquals(10, BootPlanAgent.iters(0));
        assertEquals(10, BootPlanAgent.iters(-1));
    }

    @Test
    void generateOptionsStayUnsetWhenBlank() throws Exception {
        Path workspace = Files.createTempDirectory("boot-generate");
        HarnessAgent agent = BootPlanAgent.create(mock(Model.class), new Toolkit(), workspace, "plans");
        try {
            assertNull(BootPlanAgent.generateOptions(null, null, null));
            assertNull(agent.getDelegate().getGenerateOptions());
        } finally {
            agent.close();
        }
    }

    @Test
    void generateOptionsAreApplied() throws Exception {
        Path workspace = Files.createTempDirectory("boot-generate");
        GenerateOptions options = BootPlanAgent.generateOptions(0.2, 0.9, 128);
        HarnessAgent agent = BootPlanAgent.create(
                mock(Model.class),
                new Toolkit(),
                workspace,
                "plans",
                null,
                30,
                10,
                "skills",
                null,
                80000,
                2000,
                "large_tool_results",
                options);
        try {
            GenerateOptions applied = agent.getDelegate().getGenerateOptions();
            assertEquals(0.2, applied.getTemperature());
            assertEquals(0.9, applied.getTopP());
            assertEquals(128, applied.getMaxTokens());
        } finally {
            agent.close();
        }
    }

    @Test
    void illegalGenerateOptionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BootPlanAgent.generateOptions(2.1, null, null));
        assertThrows(IllegalArgumentException.class, () -> BootPlanAgent.generateOptions(null, 0.0, null));
        assertThrows(IllegalArgumentException.class, () -> BootPlanAgent.generateOptions(null, null, 0));
    }

    @Test
    void nonPositiveEvictionFallsBack() {
        ToolResultEvictionConfig config = BootPlanAgent.eviction(0, -1, " ");
        assertEquals(ToolResultEvictionConfig.DEFAULT_MAX_RESULT_CHARS, config.getMaxResultChars());
        assertEquals(ToolResultEvictionConfig.DEFAULT_PREVIEW_CHARS, config.getPreviewChars());
        assertEquals(ToolResultEvictionConfig.DEFAULT_EVICTION_PATH, config.getEvictionPath());
    }

    @Test
    void nonPositiveCompactionFallsBack() {
        CompactionConfig config = BootPlanAgent.compaction(0, -1);
        assertEquals(30, config.getTriggerMessages());
        assertEquals(10, config.getKeepMessages());
    }

    private static FilesystemTranscriptStore transcriptStore(HarnessAgent agent) throws Exception {
        for (MiddlewareBase middleware : agent.getDelegate().getMiddlewares()) {
            if (middleware instanceof TranscriptMiddleware transcript) {
                Field field = TranscriptMiddleware.class.getDeclaredField("transcriptStore");
                field.setAccessible(true);
                return (FilesystemTranscriptStore) field.get(transcript);
            }
        }
        throw new AssertionError("transcript middleware missing");
    }

    private static ToolResultEvictionConfig evictionOf(HarnessAgent agent) throws Exception {
        for (MiddlewareBase middleware : agent.getDelegate().getMiddlewares()) {
            if (middleware instanceof ToolResultEvictionMiddleware eviction) {
                Field field = ToolResultEvictionMiddleware.class.getDeclaredField("config");
                field.setAccessible(true);
                return (ToolResultEvictionConfig) field.get(eviction);
            }
        }
        throw new AssertionError("eviction middleware missing");
    }

    private static CompactionConfig configOf(HarnessAgent agent) throws Exception {
        CompactionMiddleware hook = agent.getCompactionHook();
        Field field = CompactionMiddleware.class.getDeclaredField("config");
        field.setAccessible(true);
        return (CompactionConfig) field.get(hook);
    }
}
