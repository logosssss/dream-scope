package com.zhu.scope.adapter.a2a;

import com.zhu.scope.adapter.nacos.ChatNacosClient;
import com.zhu.scope.agent.AgentHandler;
import io.a2a.spec.TransportProtocol;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import io.agentscope.core.a2a.server.transport.TransportProperties;
import io.agentscope.core.a2a.server.transport.TransportWrapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 官方 {@code agentscope-extensions-a2a-server}：组装 {@link AgentScopeA2aServer} + JSON-RPC transport。
 * web 只调本类，不 import {@code io.agentscope}。
 *
 * <p>2.0.3 没有手册里的 {@code JsonRpcTransportProperties}，用 {@link TransportProperties#builder(String)}。
 */
public final class ScopeA2aServer {

    private static final Logger log = LoggerFactory.getLogger(ScopeA2aServer.class);

    private final AgentScopeA2aServer server;

    private ScopeA2aServer(AgentScopeA2aServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    public static ScopeA2aServer create(AgentHandler chat, String publicUrl) {
        return create(chat, publicUrl, null);
    }

    public static ScopeA2aServer create(AgentHandler chat, String publicUrl, ChatNacosClient nacos) {
        URI uri = parsePublic(publicUrl);
        String jsonRpc = TransportProtocol.JSONRPC.asString();
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        String host = uri.getHost() == null || uri.getHost().isBlank() ? "127.0.0.1" : uri.getHost();
        ConfigurableAgentCard card = new ConfigurableAgentCard.Builder()
                .name("dream-scope-chat")
                .description("dream-scope 内置 chat Agent")
                .url(uri + "/a2a")
                .version("0.1.0")
                .preferredTransport(jsonRpc)
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
        var builder = AgentScopeA2aServer.builder(new ChatA2aRunner(chat))
                .agentCard(card)
                .withTransport(TransportProperties.builder(jsonRpc).host(host).port(port).path("/a2a").build());
        if (nacos != null && nacos.a2aRegistryEnabled()) {
            builder.withAgentRegistry(nacos.a2aRegistry());
            log.info("a2a server registry attached");
        }
        log.info("a2a server built publicUrl={}", uri);
        return new ScopeA2aServer(builder.build());
    }

    public Object agentCard() {
        log.info("a2a server agent-card");
        return server.getAgentCard();
    }

    public Object handleJsonRpc(String body, Map<String, String> headers) {
        log.info("a2a server jsonrpc chars={}", body == null ? 0 : body.length());
        TransportWrapper<?, ?> wrapper = server.getTransportWrapper(TransportProtocol.JSONRPC.asString());
        if (wrapper == null) {
            throw new IllegalStateException("a2a JSONRPC transport missing");
        }
        Map<String, String> hdrs = headers == null ? Map.of() : headers;
        @SuppressWarnings("unchecked")
        TransportWrapper<String, Object> jsonRpc = (TransportWrapper<String, Object>) wrapper;
        return jsonRpc.handleRequest(body == null ? "" : body, hdrs, Map.of());
    }

    public void postEndpointReady() {
        log.info("a2a server postEndpointReady");
        server.postEndpointReady();
    }

    static URI parsePublic(String publicUrl) {
        String raw = publicUrl == null || publicUrl.isBlank() ? "http://127.0.0.1:8091" : publicUrl.trim();
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return URI.create(raw);
    }
}
