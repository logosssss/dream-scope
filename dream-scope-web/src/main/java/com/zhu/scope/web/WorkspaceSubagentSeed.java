package com.zhu.scope.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 把 classpath {@code workspace/subagents/*.md} 拷到 Harness 工作区。{@code .agentscope/} 已 gitignore，启动时必须落盘。
 */
final class WorkspaceSubagentSeed {

    static final List<String> BUNDLED = List.of("weather-agent.md", "flight-agent.md");

    private WorkspaceSubagentSeed() {}

    static void copyBundled(Path workspace) {
        if (workspace == null || workspace.toString().isBlank()) {
            return;
        }
        try {
            Path destDir = workspace.resolve("subagents");
            Files.createDirectories(destDir);
            ClassLoader loader = WorkspaceSubagentSeed.class.getClassLoader();
            for (String name : BUNDLED) {
                String resource = "workspace/subagents/" + name;
                try (InputStream in = loader.getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new IllegalStateException("missing classpath resource " + resource);
                    }
                    Files.copy(in, destDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("cannot seed subagents into " + workspace, ex);
        }
    }
}
