package com.zhu.scope.agent;

/**
 * 一次流式调用的回调。实现放 web（SSE）或测试桩。
 */
public interface AgentStreamHandler {

    void onEvent(AgentEvent event);

    void onComplete();

    void onError(Throwable error);
}
