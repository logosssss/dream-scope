package com.zhu.scope.web.excption;

import com.zhu.scope.agent.AgentProviderException;
import com.zhu.scope.agent.AgentTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Agent 调用失败映射。400 / 404 仍由 Controller 用 {@code ResponseStatusException}。
 */
@RestControllerAdvice
public class AgentInvokeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentInvokeExceptionHandler.class);

    @ExceptionHandler(AgentTimeoutException.class)
    public ResponseEntity<Void> timeout(AgentTimeoutException ex) {
        log.warn("http agent timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
    }

    @ExceptionHandler(AgentProviderException.class)
    public ResponseEntity<Void> provider(AgentProviderException ex) {
        log.warn("http agent provider failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }
}
