package com.zhu.scope.boot.config;

import com.zhu.scope.boot.agent.impl.StarterChatAgent;
import com.zhu.scope.boot.agent.StarterHandler;
import com.zhu.scope.boot.agent.StarterHandlerRegistry;
import com.zhu.scope.boot.knowledge.embed.BootEmbedder;
import com.zhu.scope.boot.knowledge.embed.DashScopeEmbedder;
import com.zhu.scope.boot.knowledge.impl.BootKnowledgeAgent;
import com.zhu.scope.boot.knowledge.index.BootKnowledgeIndex;
import com.zhu.scope.boot.knowledge.store.BootPgKnowledge;
import com.zhu.scope.boot.knowledge.tool.BootRetrieveTool;
import com.zhu.scope.boot.model.BootFallback;
import com.zhu.scope.boot.a2a.BootA2aClient;
import com.zhu.scope.boot.plan.impl.BootPlanAgent;
import com.zhu.scope.boot.skill.BootNacosSkills;
import com.zhu.scope.boot.session.BootHarness;
import com.zhu.scope.boot.session.BootRedis;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.nacos.skill.NacosSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import redis.clients.jedis.JedisPooled;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Configuration;

/** 把带计划模式的 Harness 登记进 boot 自己的注册表。官方 starter 仍单独提供 ReActAgent。 */
@Configuration
@EnableConfigurationProperties(BootScopeProperties.class)
public class StarterPortsConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "agentscope.agent", name = "enabled", havingValue = "true")
    BootHarness bootHarness(
            Model model,
            Toolkit toolkit,
            BootRetrieveTool retrieve,
            BootScopeProperties props,
            Environment env,
            ObjectProvider<NacosSkillRepository> nacosSkills) {
        Objects.requireNonNull(retrieve, "retrieve");
        JedisPooled jedis = BootRedis.open(props.getRedis().getUri());
        try {
            DistributedStore store = BootRedis.store(jedis, props.getRedis().getKeyPrefix());
            HarnessAgent agent = BootPlanAgent.create(
                    model,
                    toolkit,
                    workspace(props),
                    props.getPlan().getDirectory(),
                    store,
                    props.getCompaction().getTriggerMessages(),
                    props.getCompaction().getKeepMessages(),
                    props.getSkills().getDirectory(),
                    BootFallback.open(env, props, model),
                    props.getEviction().getMaxResultChars(),
                    props.getEviction().getPreviewChars(),
                    props.getEviction().getPath(),
                    BootPlanAgent.generateOptions(
                            props.getModel().getTemperature(),
                            props.getModel().getTopP(),
                            props.getModel().getMaxTokens()),
                    props.getPlan().getMaxIters(),
                    nacosSkills.getIfAvailable());
            return new BootHarness(agent, jedis);
        } catch (RuntimeException ex) {
            jedis.close();
            throw ex;
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentscope.agent", name = "enabled", havingValue = "true")
    HarnessAgent bootPlanAgent(BootHarness bootHarness) {
        return bootHarness.agent();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentscope.agent", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "starterChatAgent")
    StarterHandler starterChatAgent(HarnessAgent bootPlanAgent, BootScopeProperties props) {
        return new StarterChatAgent(bootPlanAgent, props.getChatTimeout());
    }

    @Bean(destroyMethod = "close")
    BootKnowledgeIndex knowledgeIndex(BootScopeProperties props) {
        BootScopeProperties.Knowledge knowledge = props.getKnowledge();
        BootPgKnowledge pg = BootPgKnowledge.open(knowledge);
        BootEmbedder embedder = pg == null ? embedder(props) : null;
        return BootKnowledgeIndex.seeded(
                embedder, pg, knowledge.getChunkSize(), knowledge.getChunkOverlap(), knowledge.getScoreThreshold());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "agentscope.nacos.skill", name = "enabled", havingValue = "true")
    NacosSkillRepository nacosSkillRepository(Environment env) {
        NacosSkillRepository repository = BootNacosSkills.open(env);
        if (repository == null) {
            throw new IllegalStateException("nacos skill required but unreachable");
        }
        return repository;
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentscope.a2a.client", name = "enabled", havingValue = "true")
    A2aAgent a2aClient(Environment env) {
        A2aAgent agent = BootA2aClient.open(env);
        if (agent == null) {
            throw new IllegalStateException("a2a client url required");
        }
        return agent;
    }

    @Bean
    BootRetrieveTool bootRetrieveTool(ObjectProvider<Toolkit> toolkits, BootKnowledgeIndex knowledgeIndex) {
        BootRetrieveTool tool = new BootRetrieveTool(knowledgeIndex);
        Toolkit toolkit = toolkits.getIfAvailable();
        if (toolkit != null) {
            toolkit.registerTool(tool);
        }
        return tool;
    }

    @Bean
    @ConditionalOnMissingBean(name = "knowledgeAgent")
    StarterHandler knowledgeAgent(BootKnowledgeIndex knowledgeIndex) {
        return new BootKnowledgeAgent(knowledgeIndex);
    }

    @Bean
    StarterHandlerRegistry starterHandlerRegistry(List<StarterHandler> handlers) {
        return new StarterHandlerRegistry(handlers);
    }

    private static BootEmbedder embedder(BootScopeProperties props) {
        BootScopeProperties.Knowledge knowledge = props.getKnowledge();
        if (knowledge.getApiKey() == null || knowledge.getApiKey().isBlank()) {
            return null;
        }
        return new DashScopeEmbedder(knowledge.getApiKey(), knowledge.getEmbeddingModel());
    }

    private static Path workspace(BootScopeProperties props) {
        String configured = props.getPlan().getWorkspace();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "dream-scope-boot", "workspace");
    }
}
