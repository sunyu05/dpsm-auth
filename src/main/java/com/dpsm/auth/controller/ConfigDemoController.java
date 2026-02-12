package com.dpsm.auth.controller;

import com.dpsm.auth.config.AppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置演示控制器
 * 展示如何使用AppConfig动态配置
 *
 * @author dpsm
 */
@Slf4j
@RestController
@RequestMapping("/dpsm/demo")
@RequiredArgsConstructor
public class ConfigDemoController {

    private static final String ENABLED_KEY = "enabled";
    private static final String MESSAGE_KEY = "message";
    private static final String CONFIG_VERSION_KEY = "configVersion";

    private final AppConfigService appConfigService;

    @GetMapping("/database-config")
    public ResponseEntity<Map<String, Object>> getDatabaseConfig() {
        Map<String, Object> response = new HashMap<>();
        
        // 从AppConfig获取数据库配置
        int connectionTimeout = appConfigService.getIntValue("database.connectionTimeout", 30);
        int maxConnections = appConfigService.getIntValue("database.maxConnections", 20);
        boolean enableQueryLogging = appConfigService.getBooleanValue("database.enableQueryLogging", false);
        
        response.put("connectionTimeout", connectionTimeout);
        response.put("maxConnections", maxConnections);
        response.put("enableQueryLogging", enableQueryLogging);
        response.put(CONFIG_VERSION_KEY, appConfigService.getCurrentConfigVersion());
        
        log.info("Database config requested - timeout: {}, maxConn: {}, logging: {}", 
                connectionTimeout, maxConnections, enableQueryLogging);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/feature-flags")
    public ResponseEntity<Map<String, Object>> getFeatureFlags() {
        Map<String, Object> response = new HashMap<>();
        
        // 从AppConfig获取功能开关
        boolean enableNewFeature = appConfigService.getBooleanValue("features.enableNewFeature", false);
        boolean enableDebugMode = appConfigService.getBooleanValue("features.enableDebugMode", false);
        boolean maintenanceMode = appConfigService.getBooleanValue("features.maintenanceMode", false);
        
        response.put("enableNewFeature", enableNewFeature);
        response.put("enableDebugMode", enableDebugMode);
        response.put("maintenanceMode", maintenanceMode);
        response.put(CONFIG_VERSION_KEY, appConfigService.getCurrentConfigVersion());
        
        log.info("Feature flags requested - newFeature: {}, debug: {}, maintenance: {}", 
                enableNewFeature, enableDebugMode, maintenanceMode);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api-config")
    public ResponseEntity<Map<String, Object>> getApiConfig() {
        Map<String, Object> response = new HashMap<>();
        
        // 从AppConfig获取API配置
        int rateLimit = appConfigService.getIntValue("api.rateLimit", 1000);
        int timeout = appConfigService.getIntValue("api.timeout", 30);
        int retryAttempts = appConfigService.getIntValue("api.retryAttempts", 3);
        
        response.put("rateLimit", rateLimit);
        response.put("timeout", timeout);
        response.put("retryAttempts", retryAttempts);
        response.put(CONFIG_VERSION_KEY, appConfigService.getCurrentConfigVersion());
        
        log.info("API config requested - rateLimit: {}, timeout: {}, retries: {}", 
                rateLimit, timeout, retryAttempts);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cache-config")
    public ResponseEntity<Map<String, Object>> getCacheConfig() {
        Map<String, Object> response = new HashMap<>();
        
        // 从AppConfig获取缓存配置
        long ttl = appConfigService.getLongValue("cache.ttl", 3600L);
        int maxSize = appConfigService.getIntValue("cache.maxSize", 1000);
        boolean enabled = appConfigService.getBooleanValue("cache.enabled", true);
        
        response.put("ttl", ttl);
        response.put("maxSize", maxSize);
        response.put(ENABLED_KEY, enabled);
        response.put(CONFIG_VERSION_KEY, appConfigService.getCurrentConfigVersion());
        
        log.info("Cache config requested - ttl: {}, maxSize: {}, enabled: {}", 
                ttl, maxSize, enabled);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/maintenance-check")
    public ResponseEntity<Map<String, Object>> maintenanceCheck() {
        Map<String, Object> response = new HashMap<>();
        
        boolean maintenanceMode = appConfigService.getBooleanValue("features.maintenanceMode", false);
        
        if (maintenanceMode) {
            response.put("status", "MAINTENANCE");
            response.put(MESSAGE_KEY, "System is currently under maintenance");
            response.put(CONFIG_VERSION_KEY, appConfigService.getCurrentConfigVersion());
            
            log.warn("Maintenance mode is enabled");
            return ResponseEntity.status(503).body(response);
        } else {
            response.put("status", "OPERATIONAL");
            response.put(MESSAGE_KEY, "System is operational");
            response.put(CONFIG_VERSION_KEY, appConfigService.getCurrentConfigVersion());
            
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/all-config")
    public ResponseEntity<Map<String, Object>> getAllConfig() {
        Map<String, Object> response = new HashMap<>();
        
        if (appConfigService.isAppConfigEnabled()) {
            // 获取所有配置键和值
            for (String key : appConfigService.getAllConfigurationKeys()) {
                String value = appConfigService.getConfigurationValue(key, "");
                response.put(key, value);
            }
            
            response.put("_metadata", Map.of(
                CONFIG_VERSION_KEY, appConfigService.getCurrentConfigVersion(),
                "totalKeys", appConfigService.getAllConfigurationKeys().size(),
                ENABLED_KEY, true
            ));
        } else {
            response.put("_metadata", Map.of(
                ENABLED_KEY, false,
                MESSAGE_KEY, "AppConfig not enabled"
            ));
        }
        
        return ResponseEntity.ok(response);
    }
}