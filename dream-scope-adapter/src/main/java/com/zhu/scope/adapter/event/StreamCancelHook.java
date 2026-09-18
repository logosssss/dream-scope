package com.zhu.scope.adapter.event;

/**
 * SSE 超时 / 客户端断开时取消 {@code Flux} 订阅。domain 的 {@code AgentStreamHandler} 无取消钩子，故放 adapter。
 */
public interface StreamCancelHook {

    void bindCancel(Runnable cancel);
}
