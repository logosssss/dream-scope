package com.zhu.scope.adapter;

import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.DistributedStore;
import redis.clients.jedis.JedisPooled;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 生产 Redis：{@code RedisDistributedStore.fromJedis} 一键注入 state / baseStore / snapshot / executionGuard。
 * 不是 Maven profile。单测构造 {@link ScopeChatAgent} 仍可不连 Redis。
 */
final class ChatRedis {

    private static final Logger log = LoggerFactory.getLogger(ChatRedis.class);

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
            log.info("redis ping ok {}", safeHost(uri));
        } catch (RuntimeException ex) {
            jedis.close();
            log.warn("redis ping failed {}", safeHost(uri));
            throw new IllegalStateException("redis required but unreachable: " + uri, ex);
        }
        return jedis;
    }

    static DistributedStore store(JedisPooled jedis, String keyPrefix) {
        String prefix = keyPrefix(keyPrefix);
        log.info("redis store prefix={}", prefix);
        return RedisDistributedStore.fromJedis(jedis, prefix);
    }

    /** URI 可能带密码，日志只打 host:port。 */
    static String safeHost(String uri) {
        try {
            URI parsed = URI.create(uri);
            String host = parsed.getHost();
            if (host == null || host.isBlank()) {
                return "(unknown)";
            }
            int port = parsed.getPort();
            return port > 0 ? host + ":" + port : host;
        } catch (RuntimeException ex) {
            return "(unparseable)";
        }
    }
}
