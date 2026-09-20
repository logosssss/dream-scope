package com.zhu.scope.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.adapter.nacos.ChatNacosSettings;
import org.junit.jupiter.api.Test;

class NacosSettingsMappingTest {

    @Test
    void mapsDreamScopeNacosProperties() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getNacos().setServerAddr("10.0.0.8:8848");
        props.getNacos().setNamespace("demo");
        props.getNacos().getPrompt().setEnabled(true);
        props.getNacos().getPrompt().setSysPromptKey("chat-sys");
        props.getNacos().getA2a().setRegistryEnabled(true);
        props.getNacos().getA2a().setDiscoveryEnabled(true);

        ChatNacosSettings settings = PortsConfig.nacosSettings(props);
        assertEquals("10.0.0.8:8848", settings.serverAddr());
        assertEquals("demo", settings.namespace());
        assertTrue(settings.promptEnabled());
        assertEquals("chat-sys", settings.sysPromptKey());
        assertTrue(settings.a2aRegistryEnabled());
        assertTrue(settings.a2aDiscoveryEnabled());
        assertEquals("dream-scope-chat", settings.a2aDiscoveryAgentName());
        assertFalse(props.getNacos().isEnabled());
    }
}
