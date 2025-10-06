package com.example.demo.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.*;

import com.example.demo.model.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KubernetesService {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesService.class);

    private final CoreV1Api coreV1Api;
    private final PodMetricsService metricsService;
    private final AppsV1Api appsV1Api;
    private final NetworkingV1Api networkingV1Api;

    @Value("${kubernetes.namespace:default}")
    private String configuredNamespace;

    @PostConstruct
    public void init() {
        logger.info("Configured Kubernetes namespace: {}", configuredNamespace);
    }

    @Autowired
    public KubernetesService(ApiClient apiClient, PodMetricsService metricsService) {
        this.coreV1Api = new CoreV1Api(apiClient);
        this.metricsService = metricsService;
        this.appsV1Api = new AppsV1Api(apiClient);
        this.networkingV1Api = new NetworkingV1Api(apiClient);
        logger.info("KubernetesService initialized with CoreV1Api and PodMetricsService");
    }

    public List<String> getPodNames() throws ApiException {
        logger.debug("Fetching all pod names across all namespaces");
        try {
            V1PodList podList = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
            return podList.getItems().stream()
                    .map(pod -> pod.getMetadata().getName())
                    .collect(Collectors.toList());
        } catch (ApiException e) {
            logger.error("Failed to fetch pod names: {}", e.getResponseBody(), e);
            throw e;
        }
    }

    public List<PodInfo> getPodInfoClusterWide() throws ApiException {
        logger.debug("Fetching pod info for all namespaces");
        try {
            V1PodList podList = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
            return podList.getItems().stream().map(this::mapPodToPodInfo).collect(Collectors.toList());
        } catch (ApiException e) {
            logger.error("Failed to fetch pods for all namespaces: {}", e.getResponseBody(), e);
            throw e;
        }
    }

    private PodInfo mapPodToPodInfo(V1Pod pod) {
        Map<String, V1Container> containerSpecs = pod.getSpec().getContainers().stream()
                .collect(Collectors.toMap(V1Container::getName, container -> container));

        List<ContainerInfo> containers = new ArrayList<>();
        if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
            containers = pod.getStatus().getContainerStatuses().stream()
                    .map(status -> createContainerInfo(status, containerSpecs.get(status.getName())))
                    .collect(Collectors.toList());
        }

        Map<String, String> metrics = metricsService.getPodMetrics(
                pod.getMetadata().getNamespace(),
                pod.getMetadata().getName()
        );

        return new PodInfo(
                pod.getMetadata().getName(),
                pod.getMetadata().getNamespace(),
                pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown",
                pod.getSpec().getNodeName(),
                pod.getStatus() != null ? pod.getStatus().getHostIP() : "Unknown",
                containers,
                metrics
        );
    }

    private ContainerInfo createContainerInfo(V1ContainerStatus status, V1Container spec) {
        String state = "Unknown";
        if (status.getState() != null) {
            if (status.getState().getRunning() != null) state = "Running";
            else if (status.getState().getWaiting() != null) state = "Waiting";
            else if (status.getState().getTerminated() != null) state = "Terminated";
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
                .getItems().stream()
                .map(ns -> ns.getMetadata().getName())
                .collect(Collectors.toList());
    }

    public ResponseEntity<String> getPodLogs(String namespace, String podName, String container, int tailLines) {
        try {
            String logs = coreV1Api.readNamespacedPodLog(
                    podName, namespace, container, false, false,
                    null, null, false, null, tailLines, false);
            return ResponseEntity.ok(logs);
        } catch (ApiException e) {
            logger.error("Failed to get logs for pod {}/{}: {}", namespace, podName, e.getResponseBody(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching logs: " + e.getResponseBody());
        }
    }

    public ResponseEntity<Object> getPodDetails(String namespace, String podName) {
        try {
            V1Pod pod = coreV1Api.readNamespacedPod(podName, namespace, null);
            return pod == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(pod);
        } catch (ApiException e) {
            logger.error("Failed to get details for pod {}/{}: {}", namespace, podName, e.getResponseBody(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching pod details: " + e.getResponseBody()));
        }
    }
    

    public List<DeploymentInfo> getAllDeployments(String namespace) throws ApiException {
    if (namespace != null && !namespace.isBlank()) {
        V1DeploymentList deploymentList = appsV1Api.listNamespacedDeployment(namespace, null, null, null, null, null, null, null, null, null, null);
        return deploymentList.getItems().stream().map(deployment -> {
            DeploymentInfo info = new DeploymentInfo(deployment);
            info.setCreationTimestamp(deployment.getMetadata().getCreationTimestamp().toString()); // Add this
            return info;
        }).collect(Collectors.toList());
    } else {
        V1DeploymentList deploymentList = appsV1Api.listDeploymentForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
        return deploymentList.getItems().stream().map(deployment -> {
            DeploymentInfo info = new DeploymentInfo(deployment);
            info.setCreationTimestamp(deployment.getMetadata().getCreationTimestamp().toString()); // Add this
            return info;
        }).collect(Collectors.toList());
    }
}


    public List<ServiceInfo> getServices(String namespace) throws ApiException {
        String effectiveNamespace = (namespace != null && !namespace.isBlank()) ? namespace : configuredNamespace;
        V1ServiceList serviceList = coreV1Api.listNamespacedService(effectiveNamespace, null, null, null, null, null, null, null, null, null, null);
        return serviceList.getItems().stream().map(service -> {
            ServiceInfo info = new ServiceInfo();
            info.setName(service.getMetadata().getName());
            info.setNamespace(service.getMetadata().getNamespace());
            info.setType(service.getSpec().getType());
            info.setClusterIP(service.getSpec().getClusterIP());
            info.setLabels(service.getMetadata().getLabels());
            info.setCreationTimestamp(service.getMetadata().getCreationTimestamp().toString());
            return info;
        }).collect(Collectors.toList());
    }

    public List<IngressInfo> getIngresses(String namespace) throws ApiException {
        String effectiveNamespace = (namespace != null && !namespace.isBlank()) ? namespace : configuredNamespace;
        V1IngressList ingressList = networkingV1Api.listNamespacedIngress(effectiveNamespace, null, null, null, null, null, null, null, null, null, null);
        return ingressList.getItems().stream().map(ingress -> {
            IngressInfo info = new IngressInfo();
            info.setName(ingress.getMetadata().getName());
            info.setNamespace(ingress.getMetadata().getNamespace());
            info.setAnnotations(ingress.getMetadata().getAnnotations());
            info.setCreationTimestamp(ingress.getMetadata().getCreationTimestamp().toString());

            List<String> hosts = new ArrayList<>();
            List<String> paths = new ArrayList<>();
            if (ingress.getSpec() != null && ingress.getSpec().getRules() != null) {
                ingress.getSpec().getRules().forEach(rule -> {
                    if (rule.getHost() != null) hosts.add(rule.getHost());
                    if (rule.getHttp() != null)
                        rule.getHttp().getPaths().forEach(path -> paths.add(path.getPath()));
                });
            }
            info.setHosts(hosts);
            info.setPaths(paths);
            return info;
        }).collect(Collectors.toList());
    }
    
}
