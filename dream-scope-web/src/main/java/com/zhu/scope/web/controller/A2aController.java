package com.zhu.scope.web.controller;

import com.zhu.scope.adapter.a2a.ScopeA2aServer;
import com.zhu.scope.adapter.LogText;
import java.util.Map;

import com.zhu.scope.web.config.DreamScopeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(A2aController.class);

    private final ScopeA2aServer a2aServer;

    private final DreamScopeProperties properties;

    public A2aController(ScopeA2aServer a2aServer, DreamScopeProperties properties) {
        this.a2aServer = a2aServer;
        this.properties = properties;
    }

    @GetMapping("/.well-known/agent-card.json")
    public Object agentCard() {
        requireEnabled();
        log.info("a2a http agent-card");
        return a2aServer.agentCard();
    }

    @PostMapping("/a2a")
    public Object jsonRpc(@RequestBody String body, @RequestHeader Map<String, String> headers) {
        requireEnabled();
        log.info("a2a http jsonrpc chars={} preview={}", LogText.chars(body), LogText.preview(body, 200));
        return a2aServer.handleJsonRpc(body, headers);
    }

    private void requireEnabled() {
        if (!properties.getA2a().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "a2a disabled");
        }
    }
}
