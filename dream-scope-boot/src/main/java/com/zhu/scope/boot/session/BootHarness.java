package com.zhu.scope.boot.session;

import io.agentscope.harness.agent.HarnessAgent;
import java.util.Objects;

/** 持有 Harness，并在关闭时先关 Agent 再关 Redis。 */
public final class BootHarness implements AutoCloseable {

    private final HarnessAgent agent;

    private final AutoCloseable redis;

    public BootHarness(HarnessAgent agent, AutoCloseable redis) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.redis = redis;
    }

    public HarnessAgent agent() {
        return agent;
    }

    @Override
    public void close() {
        try {
            agent.close();
        } finally {
            if (redis != null) {
                try {
                    redis.close();
                } catch (Exception ex) {
                    throw new IllegalStateException("cannot close redis", ex);
                }
            }
        }
    }
}
