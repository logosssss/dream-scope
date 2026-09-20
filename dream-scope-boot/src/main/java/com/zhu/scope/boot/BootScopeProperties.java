package com.zhu.scope.boot;

import java.time.Duration;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "dream-scope")
public class BootScopeProperties {

    private Duration chatTimeout = Duration.ofSeconds(120);

    public void setChatTimeout(Duration chatTimeout) {
        this.chatTimeout = chatTimeout == null || chatTimeout.isZero() || chatTimeout.isNegative()
                ? Duration.ofSeconds(120)
                : chatTimeout;
    }
}
