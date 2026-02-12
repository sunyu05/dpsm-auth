package com.dpsm.auth.controller;

import com.dpsm.auth.config.AppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 安全配置管理控制器
 * 演示如何使用 AppConfig 管理认证和安全配置
 *
 * @author dpsm
 */
@Slf4j
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityConfigController {

    private final AppConfigService appConfigService;

    /**
     * 获取所有功能开关状态
     */
    @GetMapping("/features")
    public ResponseEntity<Map<String, Object>> getAllFeatures() {
        Map<String, Object> features = new HashMap<>();
        
        features.put("socialLogin", getFeatureStatus("services.dpsm-auth.features.socialLogin"));
        features.put("twoFactorAuth", getFeatureStatus("services.dpsm-auth.features.twoFactorAuth"));
        features.put("passwordReset", getFeatureStatus("services.dpsm-auth.features.passwordReset"));
        
        return ResponseEntity.ok(features);
    }

    /**
     * 获取安全配置
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getSecurityConfig() {
        Map<String, Object> security = new HashMap<>();
        
        security.put("sessionTimeoutSeconds", 
            appConfigService.getIntValue("services.dpsm-auth.security.sessionTimeoutSeconds", 3600));
        security.put("maxLoginAttempts", 
            appConfigService.getIntValue("services.dpsm-auth.security.maxLoginAttempts", 5));
        security.put("lockoutDurationSeconds", 
            appConfigService.getIntValue("services.dpsm-auth.security.lockoutDurationSeconds", 300));
        security.put("passwordMinLength", 
            appConfigService.getIntValue("services.dpsm-auth.security.passwordMinLength", 8));
        security.put("requireSpecialChar", 
            appConfigService.getBooleanValue("services.dpsm-auth.security.requireSpecialChar", true));
        security.put("requireNumber", 
            appConfigService.getBooleanValue("services.dpsm-auth.security.requireNumber", true));
        
        return ResponseEntity.ok(security);
    }

    /**
     * 获取 JWT 配置
     */
    @GetMapping("/jwt")
    public ResponseEntity<Map<String, Object>> getJwtConfig() {
        Map<String, Object> jwt = new HashMap<>();
        
        jwt.put("expirationSeconds", 
            appConfigService.getIntValue("services.dpsm-auth.jwt.expirationSeconds", 3600));
        jwt.put("refreshExpirationSeconds", 
            appConfigService.getIntValue("services.dpsm-auth.jwt.refreshExpirationSeconds", 86400));
        jwt.put("issuer", 
            appConfigService.getConfigurationValue("services.dpsm-auth.jwt.issuer", "dpsm-auth"));
        
        return ResponseEntity.ok(jwt);
    }

    /**
     * 获取限流配置
     */
    @GetMapping("/rateLimit")
    public ResponseEntity<Map<String, Object>> getRateLimitConfig() {
        Map<String, Object> rateLimit = new HashMap<>();
        
        rateLimit.put("enabled", 
            appConfigService.getBooleanValue("services.dpsm-auth.rateLimit.enabled", true));
        rateLimit.put("loginAttemptsPerMinute", 
            appConfigService.getIntValue("services.dpsm-auth.rateLimit.loginAttemptsPerMinute", 10));
        rateLimit.put("registrationAttemptsPerMinute", 
            appConfigService.getIntValue("services.dpsm-auth.rateLimit.registrationAttemptsPerMinute", 5));
        
        return ResponseEntity.ok(rateLimit);
    }

    /**
     * 获取 AppConfig 统计信息
     */
    @GetMapping("/config/stats")
    public ResponseEntity<Map<String, Object>> getConfigStats() {
        return ResponseEntity.ok(appConfigService.getConfigurationStats());
    }

    /**
     * 手动刷新配置
     */
    @PostMapping("/config/refresh")
    public ResponseEntity<Map<String, String>> refreshConfig() {
        appConfigService.manualRefresh();
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Configuration refresh triggered");
        response.put("version", appConfigService.getCurrentConfigVersion());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 辅助方法：获取功能状态
     */
    private Map<String, Object> getFeatureStatus(String configKey) {
        Map<String, Object> feature = new HashMap<>();
        
        String enabledKey = configKey + ".enabled";
        String rolloutKey = configKey + ".rolloutPercentage";
        
        if (appConfigService.hasConfiguration(enabledKey)) {
            feature.put("enabled", appConfigService.getBooleanValue(enabledKey, false));
            feature.put("rolloutPercentage", appConfigService.getIntValue(rolloutKey, 0));
        }
        
        return feature;
    }
}
