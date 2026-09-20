package com.zhu.scope.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ChatRedisTest {

    @Test
    void requireUriRejectsBlank() {
        assertThrows(IllegalStateException.class, () -> ChatRedis.requireUri(" "));
        assertEquals("redis://127.0.0.1:6379", ChatRedis.requireUri(" redis://127.0.0.1:6379 "));
    }

    @Test
    void harnessOptionsDefaultsRedis() {
        ChatHarnessOptions options =
                new ChatHarnessOptions(
                        Duration.ofSeconds(1), Path.of("ws"), 0, 0, null, "  ", null, null, null, null, null, null, null, null);
        assertEquals(ChatRedis.DEFAULT_URI, options.redisUri());
        assertEquals(ChatRedis.DEFAULT_KEY_PREFIX, options.redisKeyPrefix());
        assertEquals(true, options.planModeEnabled());
        assertEquals("plans", options.planDirectory());
    }

    @Test
    void harnessOptionsPlanModeFalseKeepsOff() {
        ChatHarnessOptions options =
                new ChatHarnessOptions(
                        Duration.ofSeconds(1), Path.of("ws"), 0, 0, null, null, null, null, null, null, null, false, "  ", null);
        assertEquals(false, options.planModeEnabled());
        assertEquals("plans", options.planDirectory());
    }

    @Test
    void harnessOptionsRejectsInvalidGenerateParams() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatHarnessOptions(
                        Duration.ofSeconds(1), Path.of("ws"), 0, 0, null, null, 3.0, null, null, null, null, null, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatHarnessOptions(
                        Duration.ofSeconds(1), Path.of("ws"), 0, 0, null, null, null, 0.0, null, null, null, null, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChatHarnessOptions(
                        Duration.ofSeconds(1), Path.of("ws"), 0, 0, null, null, null, null, 0, null, null, null, null, null));
    }
}
