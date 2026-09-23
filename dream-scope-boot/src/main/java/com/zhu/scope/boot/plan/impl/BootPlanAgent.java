package com.zhu.scope.boot.plan.impl;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import com.zhu.scope.boot.skill.BootSkills;
import com.zhu.scope.boot.subagent.BootSubagents;
import com.zhu.scope.boot.workspace.BootAgents;
import com.zhu.scope.boot.workspace.BootKnowledgeDoc;
import com.zhu.scope.boot.workspace.BootMemory;
import com.zhu.scope.boot.workspace.BootTools;
import com.zhu.scope.boot.workspace.BootTranscript;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** 用 starter 的 Model 和 Toolkit 装一个带计划模式的 HarnessAgent。 */
public final class BootPlanAgent {

    static final String PROMPT = "你是一个有帮助的助手。工具表里有 mcp__boot__echo，需要把原文回声时就调用它。"
            + "回答产品或调用方式前先调用 retrieve，再按 [1][2] 引用。"
            + "复杂任务可先调用 plan_enter，用 plan_write 写下步骤，然后直接 plan_exit 执行。普通问答不必进入计划模式。"
            + "需要把长文本收成不超过三句的摘要时，交给子 Agent summarizer。"
            + "被截断的工具结果用 read_file 读回。";

    private BootPlanAgent() {}

    public static HarnessAgent create(Model model, Toolkit toolkit, Path workspace, String planDirectory) {
        return create(model, toolkit, workspace, planDirectory, null);
    }

    public static HarnessAgent create(
            Model model, Toolkit toolkit, Path workspace, String planDirectory, DistributedStore distributedStore) {
        return create(model, toolkit, workspace, planDirectory, distributedStore, 30, 10, "skills", null);
    }

    public static HarnessAgent create(
            Model model,
            Toolkit toolkit,
            Path workspace,
            String planDirectory,
            DistributedStore distributedStore,
            int triggerMessages,
            int keepMessages) {
        return create(
                model,
                toolkit,
                workspace,
                planDirectory,
                distributedStore,
                triggerMessages,
                keepMessages,
                "skills",
                null,
                ToolResultEvictionConfig.DEFAULT_MAX_RESULT_CHARS,
                ToolResultEvictionConfig.DEFAULT_PREVIEW_CHARS,
                ToolResultEvictionConfig.DEFAULT_EVICTION_PATH);
    }

    public static HarnessAgent create(
            Model model,
            Toolkit toolkit,
            Path workspace,
            String planDirectory,
            DistributedStore distributedStore,
            int triggerMessages,
            int keepMessages,
            String skillDirectory,
            Model fallback) {
        return create(
                model,
                toolkit,
                workspace,
                planDirectory,
                distributedStore,
                triggerMessages,
                keepMessages,
                skillDirectory,
                fallback,
                ToolResultEvictionConfig.DEFAULT_MAX_RESULT_CHARS,
                ToolResultEvictionConfig.DEFAULT_PREVIEW_CHARS,
                ToolResultEvictionConfig.DEFAULT_EVICTION_PATH);
    }

    public static HarnessAgent create(
            Model model,
            Toolkit toolkit,
            Path workspace,
            String planDirectory,
            DistributedStore distributedStore,
            int triggerMessages,
            int keepMessages,
            String skillDirectory,
            Model fallback,
            int maxResultChars,
            int previewChars,
            String evictionPath) {
        return create(
                model,
                toolkit,
                workspace,
                planDirectory,
                distributedStore,
                triggerMessages,
                keepMessages,
                skillDirectory,
                fallback,
                maxResultChars,
                previewChars,
                evictionPath,
                null);
    }

    public static HarnessAgent create(
            Model model,
            Toolkit toolkit,
            Path workspace,
            String planDirectory,
            DistributedStore distributedStore,
            int triggerMessages,
            int keepMessages,
            String skillDirectory,
            Model fallback,
            int maxResultChars,
            int previewChars,
            String evictionPath,
            GenerateOptions generateOptions) {
        return create(
                model,
                toolkit,
                workspace,
                planDirectory,
                distributedStore,
                triggerMessages,
                keepMessages,
                skillDirectory,
                fallback,
                maxResultChars,
                previewChars,
                evictionPath,
                generateOptions,
                10,
                null);
    }

    public static HarnessAgent create(
            Model model,
            Toolkit toolkit,
            Path workspace,
            String planDirectory,
            DistributedStore distributedStore,
            int triggerMessages,
            int keepMessages,
            String skillDirectory,
            Model fallback,
            int maxResultChars,
            int previewChars,
            String evictionPath,
            GenerateOptions generateOptions,
            int maxIters) {
        return create(
                model,
                toolkit,
                workspace,
                planDirectory,
                distributedStore,
                triggerMessages,
                keepMessages,
                skillDirectory,
                fallback,
                maxResultChars,
                previewChars,
                evictionPath,
                generateOptions,
                maxIters,
                null);
    }

    public static HarnessAgent create(
            Model model,
            Toolkit toolkit,
            Path workspace,
            String planDirectory,
            DistributedStore distributedStore,
            int triggerMessages,
            int keepMessages,
            String skillDirectory,
            Model fallback,
            int maxResultChars,
            int previewChars,
            String evictionPath,
            GenerateOptions generateOptions,
            int maxIters,
            AgentSkillRepository nacosSkills) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(toolkit, "toolkit");
        Objects.requireNonNull(workspace, "workspace");
        try {
            Files.createDirectories(workspace);
        } catch (IOException ex) {
            throw new IllegalStateException("plan workspace failed: " + workspace, ex);
        }
        String directory = planDirectory == null || planDirectory.isBlank() ? "plans" : planDirectory;
        String skills = skillDirectory == null || skillDirectory.isBlank() ? "skills" : skillDirectory.trim();
        BootAgents.ensure(workspace);
        BootTools.ensure(workspace);
        BootKnowledgeDoc.ensure(workspace);
        BootMemory.ensure(workspace);
        BootSkills.ensure(workspace, skills);
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("chat")
                .sysPrompt(PROMPT)
                .model(model)
                .toolkit(toolkit)
                .workspace(workspace)
                .enablePlanMode()
                .planFileDirectory(directory)
                .permissionContext(PermissionContextState.builder().mode(PermissionMode.BYPASS).build())
                .disableShellTool()
                .subagent(BootSubagents.summarizer())
                .compaction(compaction(triggerMessages, keepMessages))
                .toolResultEviction(eviction(maxResultChars, previewChars, evictionPath))
                .transcriptStore(BootTranscript.open(workspace))
                .maxIters(iters(maxIters));
        if (!"skills".equals(skills)) {
            builder.disableDefaultWorkspaceSkills().skillRepository(BootSkills.open(workspace, skills));
        }
        if (nacosSkills != null) {
            builder.skillRepository(nacosSkills);
        }
        if (distributedStore != null) {
            builder.distributedStore(distributedStore);
        }
        if (useFallback(model, fallback)) {
            builder.fallbackModel(fallback);
        }
        if (generateOptions != null) {
            builder.generateOptions(generateOptions);
        }
        HarnessAgent agent = builder.build();
        agent.getToolkit().removeTool("write_file");
        agent.getToolkit().removeTool("edit_file");
        return agent;
    }

    static boolean useFallback(Model primary, Model fallback) {
        if (fallback == null) {
            return false;
        }
        String primaryName = primary.getModelName();
        String fallbackName = fallback.getModelName();
        return primaryName == null || fallbackName == null || !primaryName.equals(fallbackName);
    }

    public static int iters(int maxIters) {
        return maxIters > 0 ? maxIters : 10;
    }

    public static CompactionConfig compaction(int triggerMessages, int keepMessages) {
        int trigger = triggerMessages > 0 ? triggerMessages : 30;
        int keep = keepMessages > 0 ? keepMessages : 10;
        return CompactionConfig.builder().triggerMessages(trigger).keepMessages(keep).keepTokens(0).build();
    }

    public static ToolResultEvictionConfig eviction(int maxResultChars, int previewChars, String path) {
        int max = maxResultChars > 0 ? maxResultChars : ToolResultEvictionConfig.DEFAULT_MAX_RESULT_CHARS;
        int preview = previewChars > 0 ? previewChars : ToolResultEvictionConfig.DEFAULT_PREVIEW_CHARS;
        String directory = path == null || path.isBlank()
                ? ToolResultEvictionConfig.DEFAULT_EVICTION_PATH
                : path.trim();
        return ToolResultEvictionConfig.builder()
                .maxResultChars(max)
                .previewChars(preview)
                .evictionPath(directory)
                .excludedToolNames(ToolResultEvictionConfig.DEFAULT_EXCLUDED_TOOLS)
                .build();
    }

    /** 三个都空则返回 null，调用方不调 generateOptions。 */
    public static GenerateOptions generateOptions(Double temperature, Double topP, Integer maxTokens) {
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("temperature must be in [0, 2]");
        }
        if (topP != null && (topP <= 0.0 || topP > 1.0)) {
            throw new IllegalArgumentException("topP must be in (0, 1]");
        }
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (temperature == null && topP == null && maxTokens == null) {
            return null;
        }
        GenerateOptions.Builder builder = GenerateOptions.builder();
        if (temperature != null) {
            builder.temperature(temperature);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        return builder.build();
    }
}
