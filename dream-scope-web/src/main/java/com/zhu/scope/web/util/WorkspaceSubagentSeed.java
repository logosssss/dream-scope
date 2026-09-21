package com.zhu.scope.web.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zhu.scope.web.config.DreamScopeProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把 classpath {@code workspace/} 下的静态文件拷到 Harness 工作区。
 * {@code .agentscope/} 已 gitignore，启动时必须落盘。
 *
 * <p>{@code AGENTS.md} / {@code knowledge/KNOWLEDGE.md} / 子 Agent / 技能 / {@code tools.json} 每次启动覆盖 bundled。
 * {@code MEMORY.md} 只在缺失时写入，避免冲掉运行时巩固过的长期记忆。
 */
public final class WorkspaceSubagentSeed {

    static final List<String> BUNDLED = List.of("weather-agent.md", "flight-agent.md");

    static final List<String> BUNDLED_SKILLS = List.of("meeting-notes/SKILL.md");

    static final List<String> BUNDLED_KNOWLEDGE = List.of("KNOWLEDGE.md");

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private WorkspaceSubagentSeed() {}

    public static void copyBundled(Path workspace) {
        copyFolder(workspace, "subagents", BUNDLED);
        copyFolder(workspace, "skills", BUNDLED_SKILLS);
        copyFolder(workspace, "knowledge", BUNDLED_KNOWLEDGE);
        copyRootFile(workspace, "tools.json", true);
        copyRootFile(workspace, "AGENTS.md", true);
        copyRootFile(workspace, "MEMORY.md", false);
        ensureDir(workspace, "memory");
    }

    /**
     * 把 MCP 列表写成 Harness 声明式 {@code tools.json}。产品路径已改走 {@code McpClientBuilder}，
     * {@code PortsConfig} 传入空列表，避免双连。
     */
    public static void applyMcpServers(Path workspace, List<DreamScopeProperties.McpServerSettings> servers) {
        if (workspace == null || workspace.toString().isBlank()) {
            return;
        }
        Map<String, Object> mcpServers = new LinkedHashMap<>();
        if (servers != null) {
            for (DreamScopeProperties.McpServerSettings server : servers) {
                if (server == null) {
                    continue;
                }
                String name = blankToNull(server.getName());
                String url = blankToNull(server.getUrl());
                if (name == null && url == null) {
                    continue;
                }
                if (name == null || url == null) {
                    throw new IllegalArgumentException("mcp server requires name and url");
                }
                String transport = server.getTransport() == null || server.getTransport().isBlank()
                        ? "streamableHttp"
                        : server.getTransport().trim();
                String normalized = transport.toLowerCase(Locale.ROOT);
                if ("stdio".equals(normalized)) {
                    throw new IllegalArgumentException("mcp stdio transport is not allowed");
                }
                if (!"streamablehttp".equals(normalized) && !"sse".equals(normalized)) {
                    throw new IllegalArgumentException("mcp transport must be streamableHttp or sse");
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    throw new IllegalArgumentException("mcp url must be http/https");
                }
                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("transport", "sse".equals(normalized) ? "sse" : "streamableHttp");
                spec.put("url", url);
                mcpServers.put(name, spec);
            }
        }
        try {
            Files.createDirectories(workspace);
            Path dest = workspace.resolve("tools.json");
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("mcpServers", mcpServers);
            JSON.writeValue(dest.toFile(), root);
        } catch (IOException ex) {
            throw new IllegalStateException("cannot write tools.json into " + workspace, ex);
        }
    }

    private static void copyRootFile(Path workspace, String name, boolean overwrite) {
        if (workspace == null || workspace.toString().isBlank()) {
            return;
        }
        try {
            Files.createDirectories(workspace);
            Path dest = workspace.resolve(name);
            if (!overwrite && Files.exists(dest)) {
                return;
            }
            String resource = "workspace/" + name;
            try (InputStream in = WorkspaceSubagentSeed.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("missing classpath resource " + resource);
                }
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("cannot seed " + name + " into " + workspace, ex);
        }
    }

    private static void ensureDir(Path workspace, String folder) {
        if (workspace == null || workspace.toString().isBlank()) {
            return;
        }
        try {
            Files.createDirectories(workspace.resolve(folder));
        } catch (IOException ex) {
            throw new IllegalStateException("cannot create " + folder + " in " + workspace, ex);
        }
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
