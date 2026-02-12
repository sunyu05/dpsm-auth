package com.dpsm.auth.controller;

import com.dpsm.auth.config.AppConfigService;
import com.dpsm.auth.config.ServiceDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.servicediscovery.model.HttpInstanceSummary;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康检查控制器
 *
 * @author dpsm
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
@RequiredArgsConstructor
public class HealthController {

    private static final String STATUS_KEY = "status";

    private final AppConfigService appConfigService;
    private final ServiceDiscoveryService serviceDiscoveryService;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${aws.region:unknown}")
    private String awsRegion;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put(STATUS_KEY, "UP");
        response.put("application", applicationName);
        response.put("timestamp", LocalDateTime.now());
        response.put("region", awsRegion);
        
        // AppConfig状态
        Map<String, Object> appConfigStatus = new HashMap<>();
        appConfigStatus.put("enabled", appConfigService.isAppConfigEnabled());
        if (appConfigService.isAppConfigEnabled()) {
            appConfigStatus.put(STATUS_KEY, "CONNECTED");
        } else {
            appConfigStatus.put(STATUS_KEY, "DISABLED");
        }
        response.put("appConfig", appConfigStatus);
        
        // Service Discovery状态
        Map<String, Object> serviceDiscoveryStatus = new HashMap<>();
        serviceDiscoveryStatus.put("enabled", serviceDiscoveryService.isServiceDiscoveryEnabled());
        if (serviceDiscoveryService.isServiceDiscoveryEnabled()) {
            serviceDiscoveryStatus.put(STATUS_KEY, "CONNECTED");
        } else {
            serviceDiscoveryStatus.put(STATUS_KEY, "DISABLED");
        }
        response.put("serviceDiscovery", serviceDiscoveryStatus);
        
        log.debug("Health check requested");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("application", applicationName);
        response.put("version", "1.0.0");
        response.put("environment", System.getProperty("spring.profiles.active", "default"));
        response.put("region", awsRegion);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> services() {
        Map<String, Object> response = new HashMap<>();
        
        if (serviceDiscoveryService.isServiceDiscoveryEnabled()) {
            try {
                // 发现当前服务的实例
                List<HttpInstanceSummary> instances = serviceDiscoveryService.discoverService("dpsm-api");
                response.put("dpsm-api-instances", instances.size());
                response.put("instances", instances);
                
                // 获取服务端点
                String endpoint = serviceDiscoveryService.getServiceEndpoint("dpsm-api");
                response.put("service-endpoint", endpoint);
                
            } catch (Exception e) {
                log.error("Failed to discover services", e);
                response.put("error", "Failed to discover services: " + e.getMessage());
            }
        } else {
            response.put("message", "Service discovery not enabled");
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> response = new HashMap<>();
        
        if (appConfigService.isAppConfigEnabled()) {
            // 获取配置统计信息
            Map<String, Object> stats = appConfigService.getConfigurationStats();
            response.putAll(stats);
            
            // 获取一些示例配置值
            response.put("database.connectionTimeout", 
                appConfigService.getIntValue("database.connectionTimeout", 30));
            response.put("database.maxConnections", 
                appConfigService.getIntValue("database.maxConnections", 20));
            response.put("features.enableNewFeature", 
                appConfigService.getBooleanValue("features.enableNewFeature", false));
            response.put("features.maintenanceMode", 
                appConfigService.getBooleanValue("features.maintenanceMode", false));
            
            // 获取所有配置键
            response.put("allConfigKeys", appConfigService.getAllConfigurationKeys());
            
        } else {
            response.put(STATUS_KEY, "AppConfig not enabled");
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/config/refresh")
    public ResponseEntity<Map<String, Object>> refreshConfig() {
        Map<String, Object> response = new HashMap<>();
        
        if (appConfigService.isAppConfigEnabled()) {
            try {
                appConfigService.manualRefresh();
                response.put(STATUS_KEY, "Configuration refresh initiated");
                response.put("timestamp", LocalDateTime.now());
            } catch (Exception e) {
                log.error("Failed to refresh configuration", e);
                response.put(STATUS_KEY, "Configuration refresh failed");
                response.put("error", e.getMessage());
            }
        } else {
            response.put(STATUS_KEY, "AppConfig not enabled");
        }
        
        return ResponseEntity.ok(response);
    }
}