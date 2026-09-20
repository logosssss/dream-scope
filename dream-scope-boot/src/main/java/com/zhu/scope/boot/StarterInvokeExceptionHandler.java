package com.zhu.scope.boot;

import com.zhu.scope.agent.AgentProviderException;
import com.zhu.scope.agent.AgentTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class StarterInvokeExceptionHandler {

    @ExceptionHandler(AgentTimeoutException.class)
    public ResponseEntity<Void> timeout() {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
    }

    @ExceptionHandler(AgentProviderException.class)
    public ResponseEntity<Void> provider() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }
}
