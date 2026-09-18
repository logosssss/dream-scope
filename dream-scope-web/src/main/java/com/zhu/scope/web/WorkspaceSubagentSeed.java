package com.zhu.scope.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 把 classpath {@code workspace/subagents/*.md} 与 {@code workspace/skills/**} 拷到 Harness 工作区。
 * {@code .agentscope/} 已 gitignore，启动时必须落盘。
 */
final class WorkspaceSubagentSeed {

    static final List<String> BUNDLED = List.of("weather-agent.md", "flight-agent.md");

    static final List<String> BUNDLED_SKILLS = List.of("meeting-notes/SKILL.md");

    private WorkspaceSubagentSeed() {}

    static void copyBundled(Path workspace) {
        copyFolder(workspace, "subagents", BUNDLED);
        copyFolder(workspace, "skills", BUNDLED_SKILLS);
    }

    private static void copyFolder(Path workspace, String folder, List<String> relativeNames) {
        if (workspace == null || workspace.toString().isBlank()) {
            return;
        }
        try {
            Path destDir = workspace.resolve(folder);
            Files.createDirectories(destDir);
            ClassLoader loader = WorkspaceSubagentSeed.class.getClassLoader();
            for (String name : relativeNames) {
                String resource = "workspace/" + folder + "/" + name;
                Path dest = destDir.resolve(name);
                Files.createDirectories(dest.getParent());
                try (InputStream in = loader.getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new IllegalStateException("missing classpath resource " + resource);
                    }
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("cannot seed " + folder + " into " + workspace, ex);
        }
    }
}
