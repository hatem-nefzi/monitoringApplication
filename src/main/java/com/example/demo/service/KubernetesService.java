package com.example.demo.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;


import com.example.demo.model.ContainerInfo;
import com.example.demo.model.PodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KubernetesService {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesService.class);

    private final CoreV1Api coreV1Api;
    private final PodMetricsService metricsService;

    @Autowired
    public KubernetesService(ApiClient apiClient, PodMetricsService metricsService) {
        this.coreV1Api = new CoreV1Api(apiClient);
        this.metricsService = metricsService;
        logger.info("KubernetesService initialized with CoreV1Api and PodMetricsService");
    }

    public List<String> getPodNames() throws ApiException {
        logger.debug("Fetching all pod names across all namespaces");
        try {
            V1PodList podList = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
            List<String> names = podList.getItems().stream()
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toList());
            
            logger.info("Found {} pods", names.size());
            return names;
            
        } catch (ApiException e) {
            logger.error("Failed to fetch pod names: {}", e.getResponseBody(), e);
            throw e;
        }
    }

    public List<PodInfo> getPodInfo(String namespace) throws ApiException {
        logger.debug("Fetching pod info for namespace: {}", namespace == null ? "all" : namespace);
        try {
            V1PodList podList = namespace == null ?
                coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null) :
                coreV1Api.listNamespacedPod(namespace, null, null, null, null, null, null, null, null, null, null);
            
            return podList.getItems().stream()
                .map(this::mapPodToPodInfo)
                .collect(Collectors.toList());
            
        } catch (ApiException e) {
            logger.error("Failed to fetch pods for namespace {}: {}", namespace, e.getResponseBody(), e);
            throw e;
        }
    }

    private PodInfo mapPodToPodInfo(V1Pod pod) {
        // Create mapping of container names to their specs
        Map<String, V1Container> containerSpecs = pod.getSpec().getContainers().stream()
            .collect(Collectors.toMap(V1Container::getName, container -> container));

        // Process container statuses
        List<ContainerInfo> containers = pod.getStatus().getContainerStatuses().stream()
            .map(status -> createContainerInfo(status, containerSpecs.get(status.getName())))
            .collect(Collectors.toList());

        // Get pod metrics
        Map<String, String> metrics = metricsService.getPodMetrics(
            pod.getMetadata().getNamespace(),
            pod.getMetadata().getName()
        );

        PodInfo podInfo = new PodInfo(
            pod.getMetadata().getName(),
            pod.getMetadata().getNamespace(),
            pod.getStatus().getPhase(),
            pod.getSpec().getNodeName(),
            pod.getStatus().getHostIP(),
            containers,
            metrics
        );

        logger.trace("Processed pod with metrics: {}", podInfo);
        return podInfo;
    }

    private ContainerInfo createContainerInfo(V1ContainerStatus status, V1Container spec) {
        String state = "Unknown";
        if (status.getState() != null) {
            if (status.getState().getRunning() != null) {
                state = "Running";
            } else if (status.getState().getWaiting() != null) {
                state = "Waiting";
            } else if (status.getState().getTerminated() != null) {
                state = "Terminated";
            }
        }

        return new ContainerInfo(
            status.getName(),
            spec != null ? spec.getImage() : "unknown",
            status.getReady(),
            status.getRestartCount(),
            state
        );
    }

    public List<String> getNamespaces() throws ApiException {
        return coreV1Api.listNamespace(null, null, null, null, null, null, null, null, null, null)
            .getItems()
            .stream()
            .map(ns -> ns.getMetadata().getName())
            .collect(Collectors.toList());
    }

    //methods for view logs and inspect 
    public ResponseEntity<String> getPodLogs(
        String namespace,
        String podName,
        String container,
        int tailLines) {
    
    try {
        String logs = coreV1Api.readNamespacedPodLog(
            podName,                     // String name
            namespace,                   // String namespace
            container,                   // String container (optional)
            false,                       // Boolean follow
            false,                       // Boolean insecureSkipTLSVerifyBackend
            null,                        // Integer limitBytes
            null,                        // String pretty
            false,                       // Boolean previous
            null,                        // Integer sinceSeconds
            tailLines,                   // Integer tailLines
            false                        // Boolean timestamps
        );
        
        return ResponseEntity.ok(logs);
    } catch (ApiException e) {
        logger.error("Failed to get logs for pod {}/{}: {}", namespace, podName, e.getResponseBody(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Error fetching logs: " + e.getResponseBody());
    }
}
    public ResponseEntity<Object> getPodDetails(
            String namespace,
            String podName) {
        
        try {
            V1Pod pod = coreV1Api.readNamespacedPod(
                podName,
                namespace,
                null  // pretty
            );
            
            if (pod == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(pod);
        } catch (ApiException e) {
            logger.error("Failed to get details for pod {}/{}: {}", namespace, podName, e.getResponseBody(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error fetching pod details: " + e.getResponseBody()));
        }
    }
}