package com.zhu.scope.boot.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.scope.boot.config.BootScopeProperties;
import com.zhu.scope.boot.http.bean.request.A2aInvokeHttpRequest;
import com.zhu.scope.boot.http.controller.BootA2aClientController;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class BootA2aClientTest {

    @Test
    void blankUrlUsesLocalServer() {
        A2aAgent agent = BootA2aClient.open(" ", "remote");
        assertEquals("remote", agent.getName());
    }

    @Test
    void clientBuildsWithoutCallingRemote() {
        A2aAgent agent = BootA2aClient.open("http://127.0.0.1:9", "remote");
        assertEquals("remote", agent.getName());
    }

    @Test
    void invokeReturnsRemoteText() {
        A2aAgent agent = mock(A2aAgent.class);
        when(agent.call("你好")).thenReturn(Mono.just(new UserMessage("pong")));
        BootA2aClientController controller = new BootA2aClientController(agent, new BootScopeProperties());
        assertEquals("pong", controller.invoke(new A2aInvokeHttpRequest("你好")).get("output"));
    }

    @Test
    void blankInputIsRejected() {
        BootA2aClientController controller =
                new BootA2aClientController(mock(A2aAgent.class), new BootScopeProperties());
        assertThrows(ResponseStatusException.class, () -> controller.invoke(new A2aInvokeHttpRequest(" ")));
    }
}
