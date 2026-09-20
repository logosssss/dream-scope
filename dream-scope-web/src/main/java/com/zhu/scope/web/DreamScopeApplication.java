package com.zhu.scope.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.zhu.scope")
@EnableDiscoveryClient
public class DreamScopeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DreamScopeApplication.class, args);
    }
}
