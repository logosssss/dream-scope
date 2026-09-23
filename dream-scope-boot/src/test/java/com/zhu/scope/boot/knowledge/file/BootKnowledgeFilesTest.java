package com.zhu.scope.boot.knowledge.file;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BootKnowledgeFilesTest {

    @Test
    void textFileBecomesChunks() {
        byte[] bytes = "端口 8092 的文件条目".getBytes(StandardCharsets.UTF_8);
        List<String> parts = BootKnowledgeFiles.read("note.txt", bytes, 2000, 200);
        assertTrue(parts.get(0).contains("8092"));
    }

    @Test
    void imageIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BootKnowledgeFiles.read("a.png", new byte[] {1, 2, 3}, 200, 0));
    }
}
