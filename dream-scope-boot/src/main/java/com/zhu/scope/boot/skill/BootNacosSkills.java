package com.zhu.scope.boot.skill;

import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import java.util.List;
import java.util.Properties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/** 从 Nacos 读 SKILL.md。默认关闭，不连 Nacos。 */
public final class BootNacosSkills {

    private BootNacosSkills() {}

    public static boolean enabled(Environment env) {
        return Boolean.TRUE.equals(env.getProperty("agentscope.nacos.skill.enabled", Boolean.class, false));
    }

    public static String namespace(Environment env) {
        String value = env.getProperty("agentscope.nacos.skill.namespace", "");
        return value == null ? "" : value.trim();
    }

    public static List<String> names(Environment env) {
        return Binder.get(env)
                .bind("agentscope.nacos.skill.names", Bindable.listOf(String.class))
                .orElse(List.of());
    }

    public static NacosSkillRepository open(Environment env) {
        if (!enabled(env)) {
            return null;
        }
        Properties properties = new Properties();
        properties.setProperty("serverAddr", env.getProperty("agentscope.nacos.server-addr", "127.0.0.1:8848"));
        String namespace = namespace(env);
        if (!namespace.isBlank()) {
            properties.setProperty("namespace", namespace);
        }
        put(properties, "username", env.getProperty("agentscope.nacos.username"));
        put(properties, "password", env.getProperty("agentscope.nacos.password"));
        try {
            return repository(AiFactory.createAiService(properties), namespace, names(env));
        } catch (NacosException ex) {
            throw new IllegalStateException("nacos skill required but unreachable", ex);
        }
    }

    public static NacosSkillRepository repository(AiService ai, String namespace, List<String> names) {
        List<String> known = names == null ? List.of() : List.copyOf(names);
        return new NacosSkillRepository(ai, namespace == null ? "" : namespace, new Properties(), known);
    }

    private static void put(Properties properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.setProperty(key, value.trim());
        }
    }
}
