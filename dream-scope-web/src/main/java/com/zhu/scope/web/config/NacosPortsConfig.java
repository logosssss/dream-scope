package com.zhu.scope.web.config;

import com.zhu.scope.adapter.nacos.ChatNacosClient;
import com.zhu.scope.adapter.nacos.ChatNacosSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Nacos AI 客户端。未开 {@code dream-scope.nacos.enabled} 时不建 Bean。 */
@Configuration
public class NacosPortsConfig {

    private static final Logger log = LoggerFactory.getLogger(NacosPortsConfig.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "dream-scope.nacos", name = "enabled", havingValue = "true")
    ChatNacosClient chatNacosClient(DreamScopeProperties props) {
        log.info(
                "nacos ai bean open serverAddr={} namespace={} prompt={} a2aRegistry={} a2aDiscovery={} skill={}",
                props.getNacos().getServerAddr(),
                props.getNacos().getNamespace(),
                props.getNacos().getPrompt().isEnabled(),
                props.getNacos().getA2a().isRegistryEnabled(),
                props.getNacos().getA2a().isDiscoveryEnabled(),
                props.getNacos().getSkill().isEnabled());
        return ChatNacosClient.open(nacosSettings(props));
    }

    public static ChatNacosSettings nacosSettings(DreamScopeProperties props) {
        DreamScopeProperties.NacosSettings nacos = props.getNacos();
        DreamScopeProperties.PromptSettings prompt = nacos.getPrompt();
        DreamScopeProperties.A2aNacosSettings a2a = nacos.getA2a();
        DreamScopeProperties.SkillSettings skill = nacos.getSkill();
        return new ChatNacosSettings(
                nacos.getServerAddr(),
                nacos.getNamespace(),
                nacos.getUsername(),
                nacos.getPassword(),
                prompt.isEnabled(),
                prompt.getSysPromptKey(),
                prompt.getVersion(),
                prompt.getLabel(),
                prompt.getVariables(),
                a2a.isRegistryEnabled(),
                a2a.isDiscoveryEnabled(),
                a2a.getDiscoveryAgentName(),
                a2a.isRegisterAsLatest(),
                a2a.isRegisterEndpoint(),
                a2a.isStreaming(),
                skill.isEnabled(),
                skill.getNames(),
                skill.getVersion(),
                skill.getLabel());
    }
}
