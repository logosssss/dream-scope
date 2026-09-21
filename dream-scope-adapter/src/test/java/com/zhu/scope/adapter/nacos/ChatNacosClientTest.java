package com.zhu.scope.adapter.nacos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.exception.NacosException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatNacosClientTest {

    @Test
    void probeTreatsMissingAgentAsHealthyChannel() {
        assertFalse(ChatNacosClient.isFatalAiProbe(new NacosException(NacosException.NOT_FOUND, "no agent")));
        assertFalse(ChatNacosClient.isFatalAiProbe(
                new NacosException(NacosException.RESOURCE_NOT_FOUND, "missing")));
    }

    @Test
    void probeTreatsOldServerOrDisconnectAsFatal() {
        assertTrue(ChatNacosClient.isFatalAiProbe(
                new NacosException(NacosException.SERVER_NOT_IMPLEMENTED, "not support")));
        assertTrue(ChatNacosClient.isFatalAiProbe(new NacosException(500, "Request Nacos server version is too low")));
        assertTrue(ChatNacosClient.isFatalAiProbe(new NacosException(500, "Connection refused")));
    }

    @Test
    void a2aRegistryIsCached() {
        ChatNacosSettings settings = settings(false);
        ChatNacosClient client = new ChatNacosClient(settings, mock(AiService.class));
        assertSame(client.a2aRegistry(), client.a2aRegistry());
    }

    @Test
    void promptMiddlewareAbsentWhenDisabled() {
        ChatNacosClient client = new ChatNacosClient(settings(false), mock(AiService.class));
        org.junit.jupiter.api.Assertions.assertNull(client.promptMiddleware());
        org.junit.jupiter.api.Assertions.assertNull(client.skillRepository());
    }

    private static ChatNacosSettings settings(boolean promptEnabled) {
        return new ChatNacosSettings(
                "127.0.0.1:8848",
                "public",
                null,
                null,
                promptEnabled,
                promptEnabled ? "dream-scope-chat" : null,
                null,
                null,
                Map.of(),
                false,
                false,
                "dream-scope-chat",
                true,
                true,
                false,
                false,
                List.of(),
                null,
                null);
    }
}
