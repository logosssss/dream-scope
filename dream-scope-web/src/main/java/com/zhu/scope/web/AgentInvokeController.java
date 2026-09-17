package com.zhu.scope.web;

import com.zhu.scope.agent.AgentHandler;
import com.zhu.scope.agent.AgentInvokeRequest;
import com.zhu.scope.agent.AgentInvokeResult;
import com.zhu.scope.agent.AgentRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 同步调用入口。只认 domain 类型。
 */
@RestController
public class AgentInvokeController {

    private final AgentRegistry registry;

    public AgentInvokeController(AgentRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/api/agents/invoke")
    public AgentInvokeHttpResponse invoke(@RequestBody AgentInvokeHttpRequest body) {
        AgentInvokeRequest request = toDomain(body);
        if (!request.hasInput()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input required");
        }
        AgentHandler handler = registry
                .find(request.agentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "unknown agent: " + request.agentId()));
        AgentInvokeResult result = handler.handle(request);
        return new AgentInvokeHttpResponse(result.agentId(), result.output());
    }

    private static AgentInvokeRequest toDomain(AgentInvokeHttpRequest body) {
        if (body == null) {
            return new AgentInvokeRequest(null, null, null, "");
        }
        return new AgentInvokeRequest(body.agentId(), body.sessionId(), body.userId(), body.input());
    }
}
