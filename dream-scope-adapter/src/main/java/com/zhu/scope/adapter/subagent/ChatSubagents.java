package com.zhu.scope.adapter.subagent;

import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import java.util.List;

/**
 * 编程式子 Agent 声明。与 workspace {@code subagents/*.md} 并存，由 Harness {@code .subagents(...)} 注册。
 */
public final class ChatSubagents {

    public static final String SUMMARIZER_ID = "summarizer";

    private ChatSubagents() {}

    public static List<SubagentDeclaration> programmatic() {
        return List.of(summarizer());
    }

    static SubagentDeclaration summarizer() {
        return SubagentDeclaration.builder()
                .name(SUMMARIZER_ID)
                .description("把用户给出的文本缩成不超过三句的中文摘要。")
                .inlineAgentsBody("你是摘要子 Agent。禁止调用任何工具。只输出摘要正文，不要标题或前缀。")
                .maxIters(4)
                .build();
    }
}
