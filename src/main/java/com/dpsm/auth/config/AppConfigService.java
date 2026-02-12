package com.dpsm.auth.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

/**
 * AWS AppConfig服务
 * 用于动态配置管理
 *
 * @author dpsm
 */
@Slf4j
@Service
public class AppConfigService {

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(
            // 核心线程数
            1,
            r -> {
                Thread t = new Thread(r, "AppConfigScheduler");
                // 设置为守护线程，避免阻塞应用关闭
                t.setDaemon(true);
                return t;
            },
            // 拒绝策略
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * 配置缓存
     */
    private final ConcurrentHashMap<String, Object> configurationCache = new ConcurrentHashMap<>();
    @Value("${aws.region:us-east-1}")
    private String awsRegion;
    @Value("${appconfig.application-id:}")
    private String applicationId;
    @Value("${appconfig.environment:dev}")
    private String environment;
    @Value("${appconfig.configuration-profile:application-config}")
    private String configurationProfile;
    private AppConfigDataClient appConfigDataClient;
    private String configurationToken;
    /**
     * 配置版本
     */
    @Getter
    private volatile String currentConfigVersion = "unknown";

    @PostConstruct
    public void init() {
        if (applicationId == null || applicationId.isEmpty()) {
            log.warn("AppConfig application ID not configured, skipping AppConfig initialization");
            return;
        }

        this.appConfigDataClient = AppConfigDataClient.builder()
                .region(Region.of(awsRegion))
                // 显式设置凭证提供者
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();

        // 初始化配置会话
        initializeConfigurationSession();

        // 定期刷新配置 (每5分钟)
        scheduler.scheduleAtFixedRate(this::refreshConfiguration, 5, 5, TimeUnit.MINUTES);

        log.info("AppConfig service initialized for application: {}, environment: {}",
                applicationId, environment);
    }

    @PreDestroy
    public void destroy() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                long timeout = 10;
                if (!scheduler.awaitTermination(timeout, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (appConfigDataClient != null) {
            appConfigDataClient.close();
        }
    }

    /**
     * 初始化配置会话
     */
    private void initializeConfigurationSession() {
        try {
            StartConfigurationSessionRequest sessionRequest = StartConfigurationSessionRequest.builder()
                    .applicationIdentifier(applicationId)
                    .environmentIdentifier(environment)
                    .configurationProfileIdentifier(configurationProfile)
                    .build();

            StartConfigurationSessionResponse sessionResponse = appConfigDataClient.startConfigurationSession(sessionRequest);
            this.configurationToken = sessionResponse.initialConfigurationToken();

            log.info("AppConfig session initialized, token: {}", configurationToken);

            // 立即获取初始配置
            refreshConfiguration();
        } catch (Exception e) {
            log.error("Failed to initialize AppConfig session", e);
        }
    }

    /**
     * 刷新配置
     */
    private void refreshConfiguration() {
        try {
            GetLatestConfigurationRequest request = GetLatestConfigurationRequest.builder()
                    .configurationToken(configurationToken)
                    .build();

            GetLatestConfigurationResponse response = appConfigDataClient.getLatestConfiguration(request);

            if (response.configuration() != null && response.configuration().asByteArray().length > 0) {
                String configContent = response.configuration().asUtf8String();
                this.configurationToken = response.nextPollConfigurationToken();
                this.currentConfigVersion = response.versionLabel() != null ? response.versionLabel() : "unknown";

                log.info("Configuration refreshed from AppConfig, version: {}", currentConfigVersion);
                log.debug("Configuration content: {}", configContent);

                // 处理配置内容
                processConfiguration(configContent);
            } else {
                log.debug("No configuration changes detected, version: {}", currentConfigVersion);
                // 更新token以便下次轮询
                this.configurationToken = response.nextPollConfigurationToken();
            }
        } catch (BadRequestException e) {
            log.error("Bad request to AppConfig: {}", e.getMessage());
            // 重新初始化会话
            initializeConfigurationSession();
        } catch (ResourceNotFoundException e) {
            log.error("AppConfig resource not found: {}", e.getMessage());
        } catch (ThrottlingException e) {
            log.warn("AppConfig request throttled, will retry later: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to refresh configuration from AppConfig", e);
        }
    }

    /**
     * 处理配置内容
     * 解析JSON配置并更新缓存
     */
    private void processConfiguration(String configContent) {
        try {
            if (configContent == null || configContent.trim().isEmpty()) {
                log.warn("Configuration content is empty");
                return;
            }

            // 解析JSON配置
            JsonNode rootNode = objectMapper.readTree(configContent);

            // 清空旧配置
            configurationCache.clear();

            // 递归处理配置节点
            processJsonNode("", rootNode);

            log.info("Configuration processed successfully, {} keys loaded", configurationCache.size());

            // 打印加载的配置键（调试用）
            if (log.isDebugEnabled()) {
                configurationCache.keySet().forEach(key ->
                        log.debug("Loaded config key: {} = {}", key, configurationCache.get(key)));
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse configuration JSON: {}", configContent, e);
        } catch (Exception e) {
            log.error("Failed to process configuration", e);
        }
    }

    /**
     * 递归处理JSON节点，构建扁平化的配置键
     */
    private void processJsonNode(String prefix, JsonNode node) {
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                String key = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;
                processJsonNode(key, node.get(fieldName));
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String key = prefix + "[" + i + "]";
                processJsonNode(key, node.get(i));
            }
        } else {
            // 叶子节点，存储值
            Object value;
            if (node.isTextual()) {
                value = node.asText();
            } else if (node.isBoolean()) {
                value = node.asBoolean();
            } else if (node.isInt()) {
                value = node.asInt();
            } else if (node.isLong()) {
                value = node.asLong();
            } else if (node.isDouble()) {
                value = node.asDouble();
            } else {
                value = node.asText();
            }

            configurationCache.put(prefix, value);
        }
    }

    /**
     * 获取配置值（字符串类型）
     */
    public String getConfigurationValue(String key, String defaultValue) {
        Object value = configurationCache.get(key);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }

    /**
     * 获取配置值（布尔类型）
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        Object value = configurationCache.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        } else if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return defaultValue;
    }

    /**
     * 获取配置值（整数类型）
     */
    public int getIntValue(String key, int defaultValue) {
        Object value = configurationCache.get(key);
        if (value instanceof Integer integerValue) {
            return integerValue;
        } else if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse integer value for key {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * 获取配置值（长整数类型）
     */
    public long getLongValue(String key, long defaultValue) {
        Object value = configurationCache.get(key);
        if (value instanceof Long longValue) {
            return longValue;
        } else if (value instanceof Integer integerValue) {
            return integerValue.longValue();
        } else if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse long value for key {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * 获取配置值（双精度类型）
     */
    public double getDoubleValue(String key, double defaultValue) {
        Object value = configurationCache.get(key);
        if (value instanceof Double doubleValue) {
            return doubleValue;
        } else if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        } else if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse double value for key {}: {}", key, value);
            }
        }
        return defaultValue;
    }

    /**
     * 检查配置键是否存在
     */
    public boolean hasConfiguration(String key) {
        return configurationCache.containsKey(key);
    }

    /**
     * 获取所有配置键
     */
    public java.util.Set<String> getAllConfigurationKeys() {
        return configurationCache.keySet();
    }

    /**
     * 手动刷新配置
     */
    public void manualRefresh() {
        log.info("Manual configuration refresh requested");
        refreshConfiguration();
    }

    /**
     * 检查AppConfig是否可用
     */
    public boolean isAppConfigEnabled() {
        return applicationId != null && !applicationId.isEmpty() && appConfigDataClient != null;
    }

    /**
     * 获取配置统计信息
     */
    public Map<String, Object> getConfigurationStats() {
        Map<String, Object> stats = HashMap.newHashMap(6);
        stats.put("enabled", isAppConfigEnabled());
        stats.put("configurationCount", configurationCache.size());
        stats.put("currentVersion", currentConfigVersion);
        stats.put("applicationId", applicationId);
        stats.put("environment", environment);
        stats.put("configurationProfile", configurationProfile);
        return stats;
    }
}
