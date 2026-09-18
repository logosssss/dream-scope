package com.zhu.scope.agent;

/**
 * Agent 调用失败。web 映射 HTTP 状态；不携带 AgentScope 类型。
 */
public class AgentInvokeException extends RuntimeException {

    public AgentInvokeException(String message) {
        super(message);
    }

    public AgentInvokeException(String message, Throwable cause) {
        super(message, cause);
    }
}
