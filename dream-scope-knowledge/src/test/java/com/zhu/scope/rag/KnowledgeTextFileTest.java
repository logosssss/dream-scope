package com.zhu.scope.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeTextFileTest {

    @Test
    void basenameAndSupport() {
        assertEquals("note.md", KnowledgeTextFile.basename("C:\\\\docs\\\\note.md"));
        assertTrue(KnowledgeTextFile.supported("note.md"));
        assertFalse(KnowledgeTextFile.supported("scan.pdf"));
        assertEquals("note", KnowledgeTextFile.stemId("../note.md"));
    }

    @Test
    void rejectsNulBytes() {
        assertThrows(IllegalArgumentException.class, () -> KnowledgeTextFile.decodeUtf8(new byte[] {'a', 0, 'b'}));
    }

    @Test
    void chunksAndIds() {
        String small = "hello";
        assertEquals(List.of("hello"), KnowledgeTextFile.chunks(small));
        assertEquals(List.of("doc"), KnowledgeTextFile.chunkIds("doc", 1));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < KnowledgeTextFile.CHUNK_CHARS + 50; i++) {
            big.append('a');
        }
        List<String> parts = KnowledgeTextFile.chunks(big.toString());
        assertEquals(2, parts.size());
        assertEquals(List.of("doc-0", "doc-1"), KnowledgeTextFile.chunkIds("doc", 2));
    }

    @Test
    void stripsUtf8Bom() {
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};
        assertEquals("hi", KnowledgeTextFile.decodeUtf8(bom));
        assertEquals("hi", KnowledgeTextFile.decodeUtf8("hi".getBytes(StandardCharsets.UTF_8)));
    }
}
