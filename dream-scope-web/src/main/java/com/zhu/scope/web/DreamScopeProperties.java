package com.zhu.scope.web;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 {@code dream-scope.*}；环境变量 {@code DREAM_SCOPE_MODEL_CHAT} 等经 relaxed binding 进来。
 */
@ConfigurationProperties(prefix = "dream-scope")
public class DreamScopeProperties {

    private final ModelSettings model = new ModelSettings();

    private Duration chatTimeout = Duration.ofSeconds(120);

    private Path workspaceDir = Path.of(".agentscope/workspace");

    private final RedisSettings redis = new RedisSettings();

    private final CompactionSettings compaction = new CompactionSettings();

    public ModelSettings getModel() {
        return model;
    }

    public Duration getChatTimeout() {
        return chatTimeout;
    }

    public void setChatTimeout(Duration chatTimeout) {
        this.chatTimeout = chatTimeout;
    }

    public Path getWorkspaceDir() {
        return workspaceDir;
    }

    public void setWorkspaceDir(Path workspaceDir) {
        this.workspaceDir = workspaceDir;
    }

    public RedisSettings getRedis() {
        return redis;
    }

    public CompactionSettings getCompaction() {
        return compaction;
    }

    public static class CompactionSettings {

        private int triggerMessages = 30;

        private int keepMessages = 10;

        public int getTriggerMessages() {
            return triggerMessages;
        }

        public void setTriggerMessages(int triggerMessages) {
            this.triggerMessages = triggerMessages;
        }

        public int getKeepMessages() {
            return keepMessages;
        }

        public void setKeepMessages(int keepMessages) {
            this.keepMessages = keepMessages;
        }
    }

    public static class RedisSettings {

        private String uri = "redis://127.0.0.1:6379";

        private String keyPrefix = "dream-scope:";

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    public static class ModelSettings {

        private String chat;

        private String defaultId;

        private Double temperature;

        private Double topP;

        private Integer maxTokens;

        private String fallback;

        public String getChat() {
            return chat;
        }

        public void setChat(String chat) {
            this.chat = chat;
        }

        /** 绑定 {@code dream-scope.model.default} / {@code DREAM_SCOPE_MODEL_DEFAULT}。 */
        public String getDefault() {
            return defaultId;
        }

        public void setDefault(String defaultId) {
            this.defaultId = defaultId;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        /** 绑定 {@code dream-scope.model.fallback} / {@code DREAM_SCOPE_MODEL_FALLBACK}。 */
        public String getFallback() {
            return fallback;
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }
    }
}
