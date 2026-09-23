package com.zhu.scope.boot.agent.excption;

/** 模型调用超时。映射为 504。 */
public final class StarterTimeoutException extends RuntimeException {

    public StarterTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
