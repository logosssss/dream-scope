package com.zhu.scope.boot.http.excption;

import com.zhu.scope.boot.agent.excption.StarterProviderException;
import com.zhu.scope.boot.agent.excption.StarterTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class StarterInvokeExceptionHandler {

    @ExceptionHandler(StarterTimeoutException.class)
    public ResponseEntity<Void> timeout() {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
    }

    @ExceptionHandler(StarterProviderException.class)
    public ResponseEntity<Void> provider() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }
}
