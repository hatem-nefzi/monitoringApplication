package com.example.demo.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.common.KubernetesListObject;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PodMetricsService {
    private static final Logger logger = LoggerFactory.getLogger(PodMetricsService.class);

    private final GenericKubernetesApi<PodMetrics, PodMetricsList> metricsApi;

    public PodMetricsService(ApiClient apiClient) {
        this.metricsApi = new GenericKubernetesApi<>(
            PodMetrics.class,
            PodMetricsList.class,
            "metrics.k8s.io",
            "v1beta1",
            "pods",
            apiClient
        );
    }

    public Map<String, String> getPodMetrics(String namespace, String podName) {
        Map<String, String> metrics = new HashMap<>();

        try {
            KubernetesApiResponse<PodMetricsList> response = metricsApi.list(namespace);

            if (!response.isSuccess()) {
                logger.error("Failed to get pod metrics: {}", response.getStatus());
                metrics.put("error", "Failed to retrieve metrics: " + response.getStatus().getMessage());
                return metrics;
            }

            PodMetricsList podMetricsList = response.getObject();
            if (podMetricsList != null && podMetricsList.getItems() != null) {
                for (PodMetrics podMetrics : podMetricsList.getItems()) {
                    if (podMetrics.getMetadata() != null &&
                        podName.equals(podMetrics.getMetadata().getName())) {

                        if (podMetrics.getContainers() != null && !podMetrics.getContainers().isEmpty()) {
                            ContainerMetrics container = podMetrics.getContainers().get(0);
                            if (container.getUsage() != null) {
                                String cpu = container.getUsage().get("cpu");
                                String memory = container.getUsage().get("memory");

                                if (cpu != null) metrics.put("cpu", cpu);
                                if (memory != null) metrics.put("memory", memory);
                            }
                        }
                        break;
                    }
                }
            }

            if (!metrics.containsKey("cpu") || !metrics.containsKey("memory")) {
                metrics.put("error", "Metrics not found for pod " + podName);
            }

        } catch (ApiException e) {
            logger.error("API error retrieving metrics for pod {}/{}: {}", namespace, podName, e.getResponseBody(), e);
            metrics.put("error", "API error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error retrieving metrics for pod {}/{}: {}", namespace, podName, e.getMessage(), e);
            metrics.put("error", "Unexpected error: " + e.getMessage());
        }

        return metrics;
    }

    // === Inner classes ===

    public static class PodMetrics implements KubernetesObject {
        @SerializedName("kind")
        private String kind;

        @SerializedName("apiVersion")
        private String apiVersion;

        @SerializedName("metadata")
        private V1ObjectMeta metadata;

        @SerializedName("containers")
        private List<ContainerMetrics> containers;

        @Override
        public V1ObjectMeta getMetadata() {
            return metadata;
        }

        @Override
        public String getApiVersion() {
            return apiVersion;
        }

        @Override
        public String getKind() {
            return kind;
        }

        public void setMetadata(V1ObjectMeta metadata) {
            this.metadata = metadata;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public List<ContainerMetrics> getContainers() {
            return containers;
        }

        public void setContainers(List<ContainerMetrics> containers) {
            this.containers = containers;
        }
    }

    public static class PodMetricsList implements KubernetesListObject {
        @SerializedName("kind")
        private String kind;

        @SerializedName("apiVersion")
        private String apiVersion;

        @SerializedName("metadata")
        private V1ListMeta metadata;

        @SerializedName("items")
        private List<PodMetrics> items;

        @Override
        public String getKind() {
            return kind;
        }

        @Override
        public String getApiVersion() {
            return apiVersion;
        }

        @Override
        public V1ListMeta getMetadata() {
            return metadata;
        }

        @Override
        public List<PodMetrics> getItems() {
            return items;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public void setMetadata(V1ListMeta metadata) {
            this.metadata = metadata;
        }

        public void setItems(List<PodMetrics> items) {
            this.items = items;
        }
    }

    public static class ContainerMetrics {
        @SerializedName("name")
        private String name;

        @SerializedName("usage")
        private Map<String, String> usage;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, String> getUsage() {
            return usage;
        }

        public void setUsage(Map<String, String> usage) {
            this.usage = usage;
        }
    }
}
