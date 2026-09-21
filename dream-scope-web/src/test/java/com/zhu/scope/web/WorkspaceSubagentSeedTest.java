package com.zhu.scope.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.web.config.DreamScopeProperties;
import com.zhu.scope.web.util.WorkspaceSubagentSeed;
import io.agentscope.harness.agent.subagent.AgentSpecLoader;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSubagentSeedTest {

    @Test
    void copiesBundledMarkdownIntoWorkspace(@TempDir Path workspace) {
        WorkspaceSubagentSeed.copyBundled(workspace);

        Path weather = workspace.resolve("subagents").resolve("weather-agent.md");
        Path flight = workspace.resolve("subagents").resolve("flight-agent.md");
        assertTrue(Files.isRegularFile(weather));
        assertTrue(Files.isRegularFile(flight));

        List<SubagentDeclaration> loaded = AgentSpecLoader.loadFromDirectory(workspace.resolve("subagents"), workspace);
        assertEquals(2, loaded.size());
        SubagentDeclaration weatherSpec = byName(loaded, "weather-agent");
        SubagentDeclaration flightSpec = byName(loaded, "flight-agent");
        assertNotNull(weatherSpec);
        assertNotNull(flightSpec);
        assertTrue(weatherSpec.getDescription().contains("天气"));
        assertTrue(flightSpec.getDescription().contains("航班"));
        assertTrue(weatherSpec.getInlineAgentsBody().contains("演示假数据"));
        assertTrue(flightSpec.getInlineAgentsBody().contains("演示假数据"));
        assertTrue(weatherSpec.getTools() == null || weatherSpec.getTools().isEmpty());
        assertTrue(flightSpec.getTools() == null || flightSpec.getTools().isEmpty());
    }

    @Test
    void copiesBundledSkillMarkdownIntoWorkspace(@TempDir Path workspace) throws Exception {
        WorkspaceSubagentSeed.copyBundled(workspace);

        Path skill = workspace.resolve("skills").resolve("meeting-notes").resolve("SKILL.md");
        assertTrue(Files.isRegularFile(skill));
        String body = Files.readString(skill);
        assertTrue(body.contains("name: meeting-notes"));
        assertTrue(body.contains("会议纪要"));
        assertFalse(body.contains("scripts/"));
    }

    @Test
    void copiesBundledToolsJsonAndWritesHttpMcp(@TempDir Path workspace) throws Exception {
        WorkspaceSubagentSeed.copyBundled(workspace);
        Path tools = workspace.resolve("tools.json");
        assertTrue(Files.isRegularFile(tools));
        assertTrue(Files.readString(tools).contains("mcpServers"));

        DreamScopeProperties.McpServerSettings server = new DreamScopeProperties.McpServerSettings();
        server.setName("weather");
        server.setTransport("streamableHttp");
        server.setUrl("https://example.com/mcp");
        WorkspaceSubagentSeed.applyMcpServers(workspace, List.of(server));
        String written = Files.readString(tools);
        assertTrue(written.contains("\"weather\""));
        assertTrue(written.contains("streamableHttp"));
        assertTrue(written.contains("https://example.com/mcp"));
    }

    @Test
    void copiesAgentsAndKnowledgeMarkdown(@TempDir Path workspace) throws Exception {
        WorkspaceSubagentSeed.copyBundled(workspace);

        Path agents = workspace.resolve("AGENTS.md");
        Path knowledge = workspace.resolve("knowledge").resolve("KNOWLEDGE.md");
        assertTrue(Files.isRegularFile(agents));
        assertTrue(Files.isRegularFile(knowledge));
        String persona = Files.readString(agents);
        assertTrue(persona.contains("# dream-scope chat"));
        assertTrue(persona.contains("## 行为"));
        String facts = Files.readString(knowledge);
        assertTrue(facts.contains("/api/agents/invoke"));
        assertTrue(facts.contains("sessionId"));
    }

    @Test
    void seedsMemoryMarkdownOnlyWhenMissing(@TempDir Path workspace) throws Exception {
        Path memory = workspace.resolve("MEMORY.md");
        Files.createDirectories(workspace);
        Files.writeString(memory, "# keep-me\n");

        WorkspaceSubagentSeed.copyBundled(workspace);

        assertTrue(Files.readString(memory).contains("keep-me"));
        assertTrue(Files.isDirectory(workspace.resolve("memory")));
    }

    @Test
    void copiesMemoryTemplateWhenAbsent(@TempDir Path workspace) throws Exception {
        WorkspaceSubagentSeed.copyBundled(workspace);
        Path memory = workspace.resolve("MEMORY.md");
        assertTrue(Files.isRegularFile(memory));
        assertTrue(Files.readString(memory).contains("长期记忆"));
        assertTrue(Files.isDirectory(workspace.resolve("memory")));
    }

    @Test
    void rejectsStdioMcp(@TempDir Path workspace) {
        DreamScopeProperties.McpServerSettings server = new DreamScopeProperties.McpServerSettings();
        server.setName("local");
        server.setTransport("stdio");
        server.setUrl("https://example.com/mcp");
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> WorkspaceSubagentSeed.applyMcpServers(workspace, List.of(server)));
    }

    private static SubagentDeclaration byName(List<SubagentDeclaration> loaded, String name) {
        return loaded.stream().filter(item -> name.equals(item.getName())).findFirst().orElse(null);
    }
}
