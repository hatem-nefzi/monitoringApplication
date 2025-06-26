package com.example.demo.service;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.MetricsApi;
import io.kubernetes.client.openapi.models.V1beta1PodMetrics;
import io.kubernetes.client.openapi.models.V1beta1PodMetricsList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PodMetricsService {
    private static final Logger logger = LoggerFactory.getLogger(PodMetricsService.class);

    private final MetricsApi metricsApi;

    public PodMetricsService(ApiClient apiClient) {
        this.metricsApi = new MetricsApi(apiClient);
    }

    public Map<String, String> getPodMetrics(String namespace, String podName) {
        Map<String, String> metrics = new HashMap<>();

        try {
            V1beta1PodMetricsList podMetricsList = metricsApi.getNamespacedPodMetrics(namespace, null, null, null);

            for (V1beta1PodMetrics podMetrics : podMetricsList.getItems()) {
                if (podMetrics.getMetadata().getName().equals(podName)) {
                    Quantity cpu = podMetrics.getContainers().get(0).getUsage().get("cpu");
                    Quantity memory = podMetrics.getContainers().get(0).getUsage().get("memory");

                    if (cpu != null) metrics.put("cpu", cpu.toSuffixedString());
                    if (memory != null) metrics.put("memory", memory.toSuffixedString());
                    break;
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
}
