package com.zhu.scope.boot.http.controller;

import com.zhu.scope.boot.agent.excption.StarterProviderException;
import com.zhu.scope.boot.agent.excption.StarterTimeoutException;
import com.zhu.scope.boot.agent.message.StarterMessages;
import com.zhu.scope.boot.config.BootScopeProperties;
import com.zhu.scope.boot.http.bean.request.A2aInvokeHttpRequest;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 把一段文本交给配置好的远端 A2A Agent。 */
@RestController
@ConditionalOnBean(A2aAgent.class)
public class BootA2aClientController {

    private final A2aAgent agent;

    private final Duration timeout;

    public BootA2aClientController(A2aAgent agent, BootScopeProperties props) {
        this.agent = agent;
        this.timeout = props.getChatTimeout();
    }

    @PostMapping("/api/a2a/invoke")
    public Map<String, String> invoke(@RequestBody(required = false) A2aInvokeHttpRequest request) {
        if (request == null || request.input() == null || request.input().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "input required");
        }
        try {
            Msg outbound = agent.call(request.input()).block(timeout);
            Map<String, String> body = new LinkedHashMap<>();
            body.put("output", StarterMessages.textOf(outbound));
            return body;
        } catch (RuntimeException ex) {
            throw map(ex);
        }
    }

    private RuntimeException map(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException) {
                return new StarterTimeoutException("a2a client timed out after " + timeout, error);
            }
        }
        return new StarterProviderException("a2a client call failed", error);
    }
}
