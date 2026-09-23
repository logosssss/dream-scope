package com.zhu.scope.boot.knowledge.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.boot.knowledge.index.BootKnowledgeIndex;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;

class BootRetrieveToolTest {

    @Test
    void citesSeededTextAndRegistersOnToolkit() {
        BootKnowledgeIndex index = BootKnowledgeIndex.seeded(null);
        BootRetrieveTool tool = new BootRetrieveTool(index);
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tool);
        assertTrue(toolkit.getToolNames().contains(BootRetrieveTool.NAME));
        assertTrue(tool.retrieve("端口 8092", 3).contains("8092"));
        assertEquals("未检索到相关资料", tool.retrieve("qqqq zzzz", 3));
    }
}
