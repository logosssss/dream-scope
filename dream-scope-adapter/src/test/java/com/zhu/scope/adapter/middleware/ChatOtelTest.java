package com.zhu.scope.adapter.middleware;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ChatOtelTest {

    @Test
    void installWithoutOtlpEndpointIsNoop() {
        System.clearProperty("otel.exporter.otlp.endpoint");
        assertFalse(ChatOtel.endpointConfigured());
        assertDoesNotThrow(ChatOtel::install);
    }
}
