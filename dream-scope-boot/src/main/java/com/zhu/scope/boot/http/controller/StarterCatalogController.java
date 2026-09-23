package com.zhu.scope.boot.http.controller;

import com.zhu.scope.boot.a2a.BootA2aClient;
import com.zhu.scope.boot.mcp.server.BootMcpDemo;
import com.zhu.scope.boot.skill.BootNacosSkills;
import com.zhu.scope.boot.workspace.BootKnowledgeDoc;
import com.zhu.scope.boot.workspace.BootMemory;
import com.zhu.scope.boot.workspace.BootTranscript;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 11 个官方 starter 的开关和入口。不调用模型。 */
@RestController
public class StarterCatalogController {

    private final Environment env;

    public StarterCatalogController(Environment env) {
        this.env = env;
    }

    @GetMapping("/api/starters")
    public Map<String, Object> list() {
        String provider = env.getProperty("agentscope.model.provider", "dashscope");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", env.getProperty("spring.application.name", "dream-scope-boot"));
        body.put("provider", provider);
        body.put("fallback", env.getProperty("dream-scope.model.fallback", ""));
        body.put("generate", generate());
        body.put("mcp", mcp());
        body.put("plan", plan());
        body.put("session", session());
        body.put("memory", memory());
        body.put("transcript", transcript());
        body.put("eviction", eviction());
        body.put("agents", agents());
        body.put("tools", tools());
        body.put("skills", skills());
        body.put("subagents", subagents());
        body.put("knowledge", knowledge());
        body.put("nacosSkill", nacosSkill());
        body.put("a2aClient", a2aClient());
        body.put(
                "starters",
                List.of(
                        item(
                                "agentscope-spring-boot-starter",
                                flag("agentscope.agent.enabled", true),
                                "ReActAgent agentscopeReActAgent"),
                        item(
                                "agentscope-dashscope-spring-boot-starter",
                                modelOn("dashscope", provider),
                                "agentscope.dashscope"),
                        item(
                                "agentscope-openai-spring-boot-starter",
                                modelOn("openai", provider),
                                "agentscope.openai"),
                        item(
                                "agentscope-anthropic-spring-boot-starter",
                                modelOn("anthropic", provider),
                                "agentscope.anthropic"),
                        item(
                                "agentscope-gemini-spring-boot-starter",
                                modelOn("gemini", provider),
                                "agentscope.gemini"),
                        item(
                                "agentscope-ollama-spring-boot-starter",
                                modelOn("ollama", provider),
                                "agentscope.ollama"),
                        item(
                                "agentscope-chat-completions-web-starter",
                                flag("agentscope.chat-completions.enabled", true),
                                "POST " + env.getProperty("agentscope.chat-completions.base-path", "/v1/chat/completions")),
                        item(
                                "agentscope-agui-spring-boot-starter",
                                true,
                                env.getProperty("agentscope.agui.path-prefix", "/agui")),
                        item(
                                "agentscope-admin-spring-boot-starter",
                                flag("agentscope.admin.enabled", false),
                                env.getProperty("agentscope.admin.base-path", "/v1/admin")),
                        item(
                                "agentscope-a2a-spring-boot-starter",
                                flag("agentscope.a2a.server.enabled", true),
                                "GET /.well-known/agent-card.json"),
                        item(
                                "agentscope-nacos-spring-boot-starter",
                                flag("agentscope.nacos.prompt.enabled", false)
                                        || flag("agentscope.a2a.nacos.enabled", false),
                                "agentscope.nacos.prompt.enabled / agentscope.a2a.nacos.enabled")));
        return body;
    }

    private Map<String, Object> generate() {
        Map<String, Object> generate = new LinkedHashMap<>();
        generate.put("temperature", env.getProperty("dream-scope.model.temperature", Double.class));
        generate.put("topP", env.getProperty("dream-scope.model.top-p", Double.class));
        generate.put("maxTokens", env.getProperty("dream-scope.model.max-tokens", Integer.class));
        return generate;
    }

    private Map<String, Object> mcp() {
        boolean enabled = flag("dream-scope.mcp.demo-enabled", true);
        String port = env.getProperty("dream-scope.mcp.demo-port", "8094");
        Map<String, Object> mcp = new LinkedHashMap<>();
        mcp.put("enabled", enabled);
        mcp.put("tool", BootMcpDemo.TOOL);
        mcp.put("url", "http://127.0.0.1:" + port + "/mcp");
        return mcp;
    }

    private Map<String, Object> knowledge() {
        String key = env.getProperty("dream-scope.knowledge.api-key", "");
        String jdbc = env.getProperty("dream-scope.knowledge.pg.jdbc-url", "");
        boolean pg = jdbc != null && !jdbc.isBlank();
        boolean embedding = key != null && !key.isBlank();
        Map<String, Object> knowledge = new LinkedHashMap<>();
        knowledge.put("enabled", true);
        knowledge.put("agentId", "knowledge");
        knowledge.put("store", pg ? "pg" : "memory");
        knowledge.put("mode", pg ? "pg" : embedding ? "embedding" : "keyword");
        knowledge.put("guide", BootKnowledgeDoc.FILE);
        return knowledge;
    }

    private Map<String, Object> eviction() {
        Map<String, Object> eviction = new LinkedHashMap<>();
        eviction.put("enabled", true);
        eviction.put("maxResultChars", env.getProperty("dream-scope.eviction.max-result-chars", Integer.class, 80000));
        eviction.put("previewChars", env.getProperty("dream-scope.eviction.preview-chars", Integer.class, 2000));
        eviction.put("path", env.getProperty("dream-scope.eviction.path", "large_tool_results"));
        return eviction;
    }

    private Map<String, Object> transcript() {
        Map<String, Object> transcript = new LinkedHashMap<>();
        transcript.put("enabled", true);
        transcript.put("directory", BootTranscript.DIRECTORY);
        return transcript;
    }

    private Map<String, Object> memory() {
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("enabled", true);
        memory.put("file", BootMemory.FILE);
        memory.put("tools", "memory_search, memory_get, memory_save, session_search, session_list, session_history");
        return memory;
    }

    private Map<String, Object> session() {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("store", "redis");
        session.put("keyPrefix", env.getProperty("dream-scope.redis.key-prefix", "dream-scope-boot:"));
        session.put("compactionTrigger", env.getProperty("dream-scope.compaction.trigger-messages", Integer.class, 30));
        session.put("compactionKeep", env.getProperty("dream-scope.compaction.keep-messages", Integer.class, 10));
        return session;
    }

    private Map<String, Object> tools() {
        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("file", "tools.json");
        tools.put("seed", true);
        tools.put("read", List.of("read_file", "list_files", "grep_files", "glob_files"));
        tools.put("deny", List.of("web_fetch", "web_search", "write_file", "edit_file"));
        return tools;
    }

    private Map<String, Object> agents() {
        Map<String, Object> agents = new LinkedHashMap<>();
        agents.put("file", "AGENTS.md");
        agents.put("seed", true);
        return agents;
    }

    private Map<String, Object> skills() {
        Map<String, Object> skills = new LinkedHashMap<>();
        skills.put("enabled", true);
        skills.put("directory", env.getProperty("dream-scope.skills.directory", "skills"));
        skills.put("demo", "boot-echo");
        return skills;
    }

    private Map<String, Object> subagents() {
        Map<String, Object> subagents = new LinkedHashMap<>();
        subagents.put("enabled", true);
        subagents.put("demo", "summarizer");
        return subagents;
    }

    private Map<String, Object> nacosSkill() {
        Map<String, Object> skill = new LinkedHashMap<>();
        skill.put("enabled", BootNacosSkills.enabled(env));
        skill.put("namespace", BootNacosSkills.namespace(env));
        skill.put("names", BootNacosSkills.names(env));
        return skill;
    }

    private Map<String, Object> a2aClient() {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("enabled", BootA2aClient.enabled(env));
        client.put("name", BootA2aClient.name(env));
        client.put("url", BootA2aClient.url(env));
        client.put("entry", "POST /api/a2a/invoke");
        return client;
    }

    private Map<String, Object> plan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("enabled", true);
        plan.put("directory", env.getProperty("dream-scope.plan.directory", "plans"));
        plan.put("maxIters", env.getProperty("dream-scope.plan.max-iters", Integer.class, 10));
        plan.put("tools", "plan_enter, plan_write, plan_exit");
        return plan;
    }

    private boolean modelOn(String name, String provider) {
        boolean selected = name.equals(provider);
        boolean enabled = flag("agentscope." + name + ".enabled", "dashscope".equals(name));
        return selected && enabled;
    }

    private boolean flag(String key, boolean defaultValue) {
        return env.getProperty(key, Boolean.class, defaultValue);
    }

    private static Map<String, Object> item(String id, boolean enabled, String entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("enabled", enabled);
        row.put("entry", entry);
        return row;
    }
}
