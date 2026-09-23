package com.zhu.scope.boot.subagent;

import io.agentscope.harness.agent.subagent.SubagentDeclaration;

/** invoke / stream 可调用的子 Agent。不打开文件工具和 shell。 */
public final class BootSubagents {

    public static final String SUMMARIZER = "summarizer";

    private BootSubagents() {}

    public static SubagentDeclaration summarizer() {
        return SubagentDeclaration.builder()
                .name(SUMMARIZER)
                .description("把用户给出的文本缩成不超过三句的中文摘要。")
                .inlineAgentsBody("你是摘要子 Agent。禁止调用任何工具。只输出摘要正文，不要标题或前缀。")
                .maxIters(4)
                .build();
    }
}
