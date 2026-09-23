package com.zhu.scope.boot.session;

import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.DistributedStore;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;

/** invoke / stream 的会话存储。地址为空或 ping 失败时抛错。 */
public final class BootRedis {

    private static final Logger log = LoggerFactory.getLogger(BootRedis.class);

    public static final String DEFAULT_PREFIX = "dream-scope-boot:";

    private BootRedis() {}

    public static String keyPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return DEFAULT_PREFIX;
        }
        return prefix.trim();
    }

    public static JedisPooled open(String uri) {
        String target = uri == null ? "" : uri.trim();
        if (target.isEmpty()) {
            throw new IllegalStateException("redis uri required");
        }
        JedisPooled jedis = new JedisPooled(target);
        try {
            jedis.ping();
            log.info("redis ping ok {}", hostOf(target));
            return jedis;
        } catch (RuntimeException ex) {
            jedis.close();
            log.warn("redis ping failed {}", hostOf(target));
            throw new IllegalStateException("redis required but unreachable: " + hostOf(target), ex);
        }
    }

    public static DistributedStore store(JedisPooled jedis, String prefix) {
        String key = keyPrefix(prefix);
        log.info("redis store prefix={}", key);
        return RedisDistributedStore.fromJedis(jedis, key);
    }

    static String hostOf(String uri) {
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
