package com.zhu.scope.adapter.nacos;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import com.zhu.scope.adapter.LogText;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 每轮推理从 Nacos 拉最新系统提示，拼在 Harness 已 build 的 {@code sysPrompt} 后面。
 *
 * <p>{@link io.agentscope.harness.agent.HarnessAgent} 的 {@code sysPrompt} 在 {@code build()} 时固化；
 * {@link io.agentscope.core.nacos.prompt.NacosPromptListener} 的订阅只有经过本中间件才会进模型。
 * 拉失败或空白时原样返回 {@code current}，不打断调用。
 */
public final class ChatNacosPromptMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ChatNacosPromptMiddleware.class);

    private final ChatNacosClient nacos;

    public ChatNacosPromptMiddleware(ChatNacosClient nacos) {
        this.nacos = Objects.requireNonNull(nacos, "nacos");
    }

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String current) {
        String base = current == null ? "" : current;
        String loaded = nacos.sysPrompt(null);
        if (loaded == null || loaded.isBlank()) {
            log.info("nacos prompt middleware skip empty currentChars={}", base.length());
            return Mono.just(base);
        }
        if (base.contains(loaded)) {
            log.info("nacos prompt middleware already applied chars={}", loaded.length());
            return Mono.just(base);
        }
        if (base.isBlank()) {
            log.info("nacos prompt middleware replace chars={} preview={}", loaded.length(), LogText.preview(loaded, 80));
            return Mono.just(loaded);
        }
        log.info("nacos prompt middleware append chars={} preview={}", loaded.length(), LogText.preview(loaded, 80));
        return Mono.just(base + "\n" + loaded);
    }
}
