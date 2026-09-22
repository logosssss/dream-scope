package com.zhu.scope.agent;

/**
 * 一次流式调用的回调。实现放 web（SSE）或测试桩。
 *
 * <p>{@link #bindCancel} 由适配层在订阅之后调用。不需要取消的桩保持默认空操作。
 */
public interface AgentStreamHandler {

    void onEvent(AgentEvent event);

    void onComplete();

    void onError(Throwable error);

    /**
     * 绑定取消动作。超时、客户端断开或同步等待失败时由调用方执行。
     * {@code cancel} 为空则忽略。
     */
    default void bindCancel(Runnable cancel) {}
}
