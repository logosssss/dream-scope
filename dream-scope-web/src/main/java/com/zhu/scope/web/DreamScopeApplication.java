package com.zhu.scope.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zhu.scope")
public class DreamScopeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DreamScopeApplication.class, args);
    }
}
