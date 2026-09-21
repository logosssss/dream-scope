package com.zhu.scope.adapter.nacos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.nacos.api.PropertyKeyConst;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ChatNacosSettingsTest {

    @Test
    void defaultsServerAndNamespace() {
        ChatNacosSettings settings = new ChatNacosSettings(
                "  ",
                null,
                " ",
                null,
                true,
                "  dream-scope-chat ",
                null,
                "prod",
                Map.of("env", "dev"),
                true,
                true,
                " dream-scope-chat ",
                true,
                false,
                false,
                false,
                List.of(),
                null,
                null);
        assertEquals("127.0.0.1:8848", settings.serverAddr());
        assertEquals("public", settings.namespace());
        assertNull(settings.username());
        assertEquals("dream-scope-chat", settings.sysPromptKey());
        assertEquals("prod", settings.promptLabel());
        assertEquals("dream-scope-chat", settings.a2aDiscoveryAgentName());
        assertTrue(settings.promptEnabled());
        assertTrue(settings.a2aRegistryEnabled());
        assertFalse(settings.registerEndpoint());
        assertFalse(settings.a2aStreaming());
        assertFalse(settings.skillEnabled());
        assertEquals("dev", settings.promptVariables().get("env"));
    }

    @Test
    void clientPropertiesCopyAddrNamespaceAndAuth() {
        ChatNacosSettings settings = new ChatNacosSettings(
                "nacos:8848",
                "ns-1",
                "nacos",
                "secret",
                false,
                null,
                null,
                null,
                Map.of(),
                false,
                false,
                null,
                true,
                true,
                false,
                false,
                List.of(),
                null,
                null);
        Properties props = ChatNacosClient.clientProperties(settings);
        assertEquals("nacos:8848", props.getProperty(PropertyKeyConst.SERVER_ADDR));
        assertEquals("ns-1", props.getProperty(PropertyKeyConst.NAMESPACE));
        assertEquals("nacos", props.getProperty(PropertyKeyConst.USERNAME));
        assertEquals("secret", props.getProperty(PropertyKeyConst.PASSWORD));
    }
}
