package com.zhu.scope.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RetrieveCitationsTest {

    @Test
    void formatsNumberedBlocks() {
        String text = RetrieveCitations.format(List.of(
                new RetrieveHit("a", "第一段", 0.9, "s", "intro"),
                new RetrieveHit("b", "第二段", 0.8, "s", "")));
        assertEquals("[1] 第一段\n---\n[2] 第二段", text);
    }

    @Test
    void emptyIsBlank() {
        assertEquals("", RetrieveCitations.format(List.of()));
        assertEquals("", RetrieveCitations.format(null));
    }
}
