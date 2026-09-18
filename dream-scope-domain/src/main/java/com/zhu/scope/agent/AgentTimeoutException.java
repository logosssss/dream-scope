package com.zhu.scope.agent;

/** 模型调用超时。web 映射为 504。 */
public final class AgentTimeoutException extends AgentInvokeException {

    public AgentTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
