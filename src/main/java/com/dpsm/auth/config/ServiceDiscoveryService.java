package com.dpsm.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.servicediscovery.model.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AWS Cloud Map服务发现
 * 用于微服务注册和发现
 *
 * @author dpsm
 */
@Slf4j
@Service
public class ServiceDiscoveryService {

    private final Map<String, String> serviceCache = new ConcurrentHashMap<>();
    @Value("${aws.region:us-east-1}")
    private String awsRegion;
    @Value("${service.discovery.namespace:}")
    private String namespaceName;
    private ServiceDiscoveryClient serviceDiscoveryClient;

    @PostConstruct
    public void init() {
        if (namespaceName == null || namespaceName.isEmpty()) {
            log.warn("Service discovery namespace not configured, skipping Cloud Map initialization");
            return;
        }

        this.serviceDiscoveryClient = ServiceDiscoveryClient.builder()
                .region(Region.of(awsRegion))
                // 显式设置凭证提供者
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();

        log.info("Service Discovery initialized for namespace: {}", namespaceName);
    }

    /**
     * 发现服务实例
     */
    public List<HttpInstanceSummary> discoverService(String serviceName) {
        if (!isServiceDiscoveryEnabled()) {
            log.warn("Service discovery not enabled");
            return List.of();
        }

        try {
            // 发现服务实例
            DiscoverInstancesRequest request = DiscoverInstancesRequest.builder()
                    .namespaceName(namespaceName)
                    .serviceName(serviceName)
                    .maxResults(10)
                    .build();

            DiscoverInstancesResponse response = serviceDiscoveryClient.discoverInstances(request);

            log.info("Discovered {} instances for service: {}",
                    response.instances().size(), serviceName);

            return response.instances();

        } catch (Exception e) {
            log.error("Failed to discover service: {}", serviceName, e);
            return List.of();
        }
    }

    /**
     * 获取服务端点
     */
    public String getServiceEndpoint(String serviceName) {
        String cacheKey = "endpoint:" + serviceName;

        // 先从缓存获取
        String cachedEndpoint = serviceCache.get(cacheKey);
        if (cachedEndpoint != null) {
            return cachedEndpoint;
        }

        // 发现服务实例
        List<HttpInstanceSummary> instances = discoverService(serviceName);
        if (!instances.isEmpty()) {
            // 取第一个实例
            HttpInstanceSummary instance = instances.getFirst();
            String endpoint = buildEndpoint(instance);

            // 缓存端点
            serviceCache.put(cacheKey, endpoint);
            return endpoint;
        }

        return null;
    }

    /**
     * 构建服务端点URL
     */
    private String buildEndpoint(HttpInstanceSummary instance) {
        Map<String, String> attributes = instance.attributes();
        String protocol = attributes.getOrDefault("protocol", "http");
        String host = attributes.getOrDefault("AWS_INSTANCE_IPV4", instance.instanceId());
        String port = attributes.getOrDefault("AWS_INSTANCE_PORT", "8080");

        return String.format("%s://%s:%s", protocol, host, port);
    }

    /**
     * 获取命名空间ID
     */
    private String getNamespaceId() {
        try {
            ListNamespacesRequest request = ListNamespacesRequest.builder()
                    .maxResults(10)
                    .build();

            ListNamespacesResponse response = serviceDiscoveryClient.listNamespaces(request);

            return response.namespaces().stream()
                    .filter(ns -> namespaceName.equals(ns.name()))
                    .map(NamespaceSummary::id)
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            log.error("Failed to get namespace ID", e);
            return null;
        }
    }

    /**
     * 获取服务ID
     */
    private String getServiceId(String namespaceId, String serviceName) {
        try {
            ListServicesRequest request = ListServicesRequest.builder()
                    .maxResults(10)
                    .filters(ServiceFilter.builder()
                            .name(ServiceFilterName.NAMESPACE_ID)
                            .values(namespaceId)
                            .build())
                    .build();

            ListServicesResponse response = serviceDiscoveryClient.listServices(request);

            return response.services().stream()
                    .filter(service -> serviceName.equals(service.name()))
                    .map(ServiceSummary::id)
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            log.error("Failed to get service ID", e);
            return null;
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        serviceCache.clear();
        log.info("Service discovery cache cleared");
    }

    /**
     * 检查服务发现是否可用
     */
    public boolean isServiceDiscoveryEnabled() {
        return namespaceName != null && !namespaceName.isEmpty() && serviceDiscoveryClient != null;
    }
}
