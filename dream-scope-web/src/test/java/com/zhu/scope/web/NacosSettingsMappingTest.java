package com.zhu.scope.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.scope.adapter.nacos.ChatNacosSettings;
import com.zhu.scope.web.config.DreamScopeProperties;
import com.zhu.scope.web.config.PortsConfig;
import org.junit.jupiter.api.Test;

class NacosSettingsMappingTest {

    @Test
    void mapsDreamScopeNacosProperties() {
        DreamScopeProperties props = new DreamScopeProperties();
        props.getNacos().setServerAddr("10.0.0.8:8848");
        props.getNacos().setNamespace("demo");
        props.getNacos().getPrompt().setEnabled(true);
        props.getNacos().getPrompt().setSysPromptKey("chat-sys");
        props.getNacos().getPrompt().setVersion("1.0.0");
        props.getNacos().getPrompt().setLabel("gray");
        props.getNacos().getPrompt().setVariables(java.util.Map.of("product", "dream-scope"));
        props.getNacos().getA2a().setRegistryEnabled(true);
        props.getNacos().getA2a().setDiscoveryEnabled(true);
        props.getNacos().getA2a().setStreaming(true);
        props.getNacos().getSkill().setEnabled(true);
        props.getNacos().getSkill().setNames(java.util.List.of("meeting-notes"));

        ChatNacosSettings settings = PortsConfig.nacosSettings(props);
        assertEquals("10.0.0.8:8848", settings.serverAddr());
        assertEquals("demo", settings.namespace());
        assertTrue(settings.promptEnabled());
        assertEquals("chat-sys", settings.sysPromptKey());
        assertEquals("1.0.0", settings.promptVersion());
        assertEquals("gray", settings.promptLabel());
        assertEquals("dream-scope", settings.promptVariables().get("product"));
        assertTrue(settings.a2aRegistryEnabled());
        assertTrue(settings.a2aDiscoveryEnabled());
        assertEquals("dream-scope-chat", settings.a2aDiscoveryAgentName());
        assertTrue(settings.a2aStreaming());
        assertTrue(settings.skillEnabled());
        assertEquals("meeting-notes", settings.skillNames().get(0));
        assertFalse(props.getNacos().isEnabled());
    }
}
