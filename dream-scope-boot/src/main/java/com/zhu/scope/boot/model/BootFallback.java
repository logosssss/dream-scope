package com.zhu.scope.boot.model;

import com.zhu.scope.boot.config.BootScopeProperties;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import org.springframework.core.env.Environment;

/** 主模型失败时的备用模型。未配置则返回空。 */
public final class BootFallback {

    private BootFallback() {}

    public static Model open(Environment env, BootScopeProperties props, Model primary) {
        String id = props.getModel().getFallback();
        if (id == null || id.isBlank()) {
            return null;
        }
        String trimmed = id.trim();
        String primaryName = primary.getModelName() == null ? "" : primary.getModelName();
        if (trimmed.equals(primaryName) || trimmed.endsWith(":" + primaryName)) {
            return null;
        }
        String key = props.getModel().getFallbackApiKey();
        if (key == null || key.isBlank()) {
            key = env.getProperty("agentscope.dashscope.api-key", "");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("fallback model api key required: " + trimmed);
        }
        return ModelRegistry.resolve(trimmed, ModelCreationContext.builder().apiKey(key.trim()).build());
    }
}
