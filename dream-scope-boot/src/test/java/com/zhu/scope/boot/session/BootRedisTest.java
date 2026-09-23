package com.zhu.scope.boot.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BootRedisTest {

    @Test
    void blankPrefixFallsBack() {
        assertEquals(BootRedis.DEFAULT_PREFIX, BootRedis.keyPrefix(" "));
        assertEquals("boot:", BootRedis.keyPrefix(" boot: "));
    }

    @Test
    void blankUriIsRejected() {
        assertThrows(IllegalStateException.class, () -> BootRedis.open(" "));
    }

    @Test
    void hostDropsPassword() {
        assertEquals("127.0.0.1:6379", BootRedis.hostOf("redis://:secret@127.0.0.1:6379"));
    }
}
