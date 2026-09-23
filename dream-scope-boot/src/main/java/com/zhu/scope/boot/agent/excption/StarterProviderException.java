package com.zhu.scope.boot.agent.excption;

/** 模型供应商或运行时失败。映射为 502。 */
public final class StarterProviderException extends RuntimeException {

    public StarterProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
