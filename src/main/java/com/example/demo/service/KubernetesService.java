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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KubernetesService {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesService.class);

    private final CoreV1Api coreV1Api;

    @Autowired
    public KubernetesService(ApiClient apiClient) {
        this.coreV1Api = new CoreV1Api(apiClient);
        logger.info("KubernetesService initialized with CoreV1Api");
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

        PodInfo podInfo = new PodInfo(
            pod.getMetadata().getName(),
            pod.getMetadata().getNamespace(),
            pod.getStatus().getPhase(),
            pod.getSpec().getNodeName(),
            pod.getStatus().getHostIP(),
            containers
        );

        logger.trace("Processed pod: {}", podInfo);
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
}