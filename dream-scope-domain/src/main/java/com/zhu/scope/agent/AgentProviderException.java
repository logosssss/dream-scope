package com.zhu.scope.agent;

/** 模型供应商或运行时失败。web 映射为 502。 */
public final class AgentProviderException extends AgentInvokeException {

    public AgentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
