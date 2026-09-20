package com.zhu.scope.adapter.middleware;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按官方 2.0.3 手册把 OpenTelemetry SDK 注册到 {@code GlobalOpenTelemetry}。
 *
 * <p>{@link io.agentscope.core.tracing.OtelTracingMiddleware} 只读全局 Tracer。未配 {@code
 * OTEL_EXPORTER_OTLP_ENDPOINT} 时不初始化，保持默认 no-op。
 */
public final class ChatOtel {

    private static final Logger log = LoggerFactory.getLogger(ChatOtel.class);

    private static final Object LOCK = new Object();

    private static boolean installed;

    private ChatOtel() {}

    public static void install() {
        if (installed || !endpointConfigured()) {
            return;
        }
        synchronized (LOCK) {
            if (installed || !endpointConfigured()) {
                return;
            }
            if (blank(System.getProperty("otel.service.name"))
                    && blank(System.getenv("OTEL_SERVICE_NAME"))) {
                System.setProperty("otel.service.name", "dream-scope");
            }
            AutoConfiguredOpenTelemetrySdk.initialize();
            installed = true;
            log.info("OpenTelemetry SDK registered (OTLP {})", otlpEndpoint());
        }
    }

    static boolean endpointConfigured() {
        return !blank(otlpEndpoint());
    }

    private static String otlpEndpoint() {
        String env = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
        if (!blank(env)) {
            return env.trim();
        }
        String property = System.getProperty("otel.exporter.otlp.endpoint");
        return property == null ? "" : property.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
