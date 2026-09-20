package com.zhu.scope.web;

import com.zhu.scope.adapter.a2a.ScopeA2aServer;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * A2A Agent Card 与 JSON-RPC。转 {@link ScopeA2aServer}（官方 a2a-server），不引入 Spring starter。
 */
@RestController
public class A2aController {

    private final ScopeA2aServer a2aServer;

    private final DreamScopeProperties properties;

    public A2aController(ScopeA2aServer a2aServer, DreamScopeProperties properties) {
        this.a2aServer = a2aServer;
        this.properties = properties;
    }

    @GetMapping("/.well-known/agent-card.json")
    public Object agentCard() {
        requireEnabled();
        return a2aServer.agentCard();
    }

    @PostMapping("/a2a")
    public Object jsonRpc(@RequestBody String body, @RequestHeader Map<String, String> headers) {
        requireEnabled();
        return a2aServer.handleJsonRpc(body, headers);
    }

    private void requireEnabled() {
        if (!properties.getA2a().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "a2a disabled");
        }
    }
}
