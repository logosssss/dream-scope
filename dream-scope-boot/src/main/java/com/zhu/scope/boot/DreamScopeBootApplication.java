package com.zhu.scope.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 官方 AgentScope starter 入口。扫描本包及子包，避免和 {@code dream-scope-web} 的 PortsConfig 叠在同一进程。
 */
@SpringBootApplication(scanBasePackages = "com.zhu.scope.boot")
@EnableDiscoveryClient
public class DreamScopeBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(DreamScopeBootApplication.class, args);
    }
}
