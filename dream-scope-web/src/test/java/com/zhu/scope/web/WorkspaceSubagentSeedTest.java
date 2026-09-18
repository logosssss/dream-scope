package com.zhu.scope.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static SubagentDeclaration byName(List<SubagentDeclaration> loaded, String name) {
        return loaded.stream().filter(item -> name.equals(item.getName())).findFirst().orElse(null);
    }
}
