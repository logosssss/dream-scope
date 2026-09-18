package com.zhu.scope.adapter.subagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import org.junit.jupiter.api.Test;

class ChatSubagentsTest {

    @Test
    void programmaticSummarizerHasNameDescriptionAndPrompt() {
        assertEquals(1, ChatSubagents.programmatic().size());
        SubagentDeclaration summarizer = ChatSubagents.summarizer();
        assertEquals(ChatSubagents.SUMMARIZER_ID, summarizer.getName());
        assertTrue(summarizer.getDescription().contains("摘要"));
        assertTrue(summarizer.getInlineAgentsBody().contains("禁止调用任何工具"));
        assertEquals(4, summarizer.getMaxIters());
    }
}
