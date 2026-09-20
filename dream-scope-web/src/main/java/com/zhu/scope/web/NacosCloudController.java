package com.zhu.scope.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring Cloud Nacos 配置中心 / 服务发现探活。与 AgentScope AI（prompt / A2A）开关独立。
 *
 * <p>发现关闭时返回空列表，不连 Nacos。
 */
@RestController
@RequestMapping("/api/nacos")
public class NacosCloudController {

    private final Environment environment;

    private final ObjectProvider<DiscoveryClient> discoveryClient;

    public NacosCloudController(Environment environment, ObjectProvider<DiscoveryClient> discoveryClient) {
        this.environment = environment;
        this.discoveryClient = discoveryClient;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", environment.getProperty("spring.application.name"));
        body.put("configEnabled", flag("spring.cloud.nacos.config.enabled"));
        body.put("discoveryEnabled", flag("spring.cloud.nacos.discovery.enabled"));
        body.put("aiEnabled", flag("dream-scope.nacos.enabled"));
        body.put("serverAddr", environment.getProperty("spring.cloud.nacos.server-addr", ""));
        body.put("services", services());
        return body;
    }

    @GetMapping("/instances/{serviceId}")
    public List<Map<String, Object>> instances(@PathVariable String serviceId) {
        DiscoveryClient client = clientIfDiscoveryOn();
        if (client == null) {
            return List.of();
        }
        try {
            return client.getInstances(serviceId).stream().map(NacosCloudController::toMap).toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<String> services() {
        DiscoveryClient client = clientIfDiscoveryOn();
        if (client == null) {
            return List.of();
        }
        try {
            return client.getServices();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private DiscoveryClient clientIfDiscoveryOn() {
        if (!flag("spring.cloud.nacos.discovery.enabled")) {
            return null;
        }
        return discoveryClient.getIfAvailable();
    }

    private boolean flag(String key) {
        return Boolean.TRUE.equals(environment.getProperty(key, Boolean.class, false));
    }

    private static Map<String, Object> toMap(ServiceInstance instance) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("serviceId", instance.getServiceId());
        row.put("instanceId", instance.getInstanceId());
        row.put("host", instance.getHost());
        row.put("port", instance.getPort());
        row.put("secure", instance.isSecure());
        row.put("uri", instance.getUri().toString());
        row.put("metadata", instance.getMetadata());
        return row;
    }
}
