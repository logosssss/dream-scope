package com.zhu.scope.adapter;

import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.DistributedStore;
import redis.clients.jedis.JedisPooled;

/**
 * 生产 Redis：{@code RedisDistributedStore.fromJedis} 一键注入 state / baseStore / snapshot / executionGuard。
 * 不是 Maven profile。单测构造 {@link ScopeChatAgent} 仍可不连 Redis。
 */
final class ChatRedis {

    static final String DEFAULT_URI = "redis://127.0.0.1:6379";

    static final String DEFAULT_KEY_PREFIX = "dream-scope:";

    private ChatRedis() {}

    static String requireUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("redis uri required");
        }
        return uri.trim();
    }

    static String keyPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return DEFAULT_KEY_PREFIX;
        }
        return prefix.trim();
    }

    static JedisPooled open(String uri) {
        JedisPooled jedis = new JedisPooled(requireUri(uri));
        try {
            jedis.ping();
        } catch (RuntimeException ex) {
            jedis.close();
            throw new IllegalStateException("redis required but unreachable: " + uri, ex);
        }
        return jedis;
    }

    static DistributedStore store(JedisPooled jedis, String keyPrefix) {
        return RedisDistributedStore.fromJedis(jedis, keyPrefix(keyPrefix));
    }
}
