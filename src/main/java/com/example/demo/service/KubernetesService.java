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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.kubernetes.client.custom.Quantity;
import com.example.demo.service.CacheService;

@Service
public class KubernetesService {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesService.class);

    private final CoreV1Api coreV1Api;
    private final PodMetricsService metricsService;
    private final AppsV1Api appsV1Api;
    private final NetworkingV1Api networkingV1Api;
    @Autowired
    private CacheService cacheService;


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
    logger.debug("Fetching pod info for all namespaces (with Redis caching)");

    // 1️⃣ Try Redis cache first
    List<PodInfo> cachedPods = cacheService.getCachedPodData();
    if (cachedPods != null && !cachedPods.isEmpty()) {
        logger.info("✅ Returning pod data from Redis cache ({} pods)", cachedPods.size());
        return cachedPods;
    }

    // 2️⃣ Cache miss → Fetch from K8s API
    try {
        logger.info("❌ Cache miss — fetching fresh data from Kubernetes API");
        V1PodList podList = coreV1Api.listPodForAllNamespaces(
                null, null, null, null, null, null, null, null, null, null);

        List<PodInfo> pods = podList.getItems().stream()
                .map(this::convertV1PodToPodInfo)
                .collect(Collectors.toList());

        // 3️⃣ Store in Redis
        cacheService.cachePodData(new ArrayList<>(pods));
        logger.info("✅ Cached {} pods in Redis", pods.size());

        return pods;
    } catch (ApiException e) {
        logger.error("Failed to fetch pods for all namespaces: {}", e.getResponseBody(), e);
        throw e;
    }
}
    /**
 * 🆕 Get fresh pods for cost analysis (bypasses cache)
 */
public List<PodInfo> getPodInfoClusterWideFresh() throws ApiException {
    logger.debug("Fetching FRESH pod info (bypassing cache for cost analysis)");
    
    V1PodList podList = coreV1Api.listPodForAllNamespaces(
        null, null, null, null, null, null, null, null, null, null);
    
    return podList.getItems().stream()
        .map(this::convertV1PodToPodInfo)
        .collect(Collectors.toList());
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
    // Add this to your existing KubernetesService.java
    public void deletePod(String namespace, String podName) throws ApiException {
    try {
        V1Pod result = coreV1Api.deleteNamespacedPod(
            podName, 
            namespace, 
            null, // pretty
            null, // dryRun
            null, // gracePeriodSeconds
            null, // orphanDependents
            null, // propagationPolicy
            null  // body
        );
        logger.info("✅ Successfully deleted pod: {}/{}", namespace, podName);
    } catch (ApiException e) {
        logger.error("❌ Failed to delete pod {}/{}: {}", namespace, podName, e.getResponseBody());
        throw e;
    }
}   
    /**
     * Get resource requests and limits for a specific pod
     */
    public Map<String, String> getPodResourceRequests(String namespace, String podName) throws ApiException {
        Map<String, String> resources = new HashMap<>();

        try {
            // Fetch the pod details
            V1Pod pod = coreV1Api.readNamespacedPod(podName, namespace, null);

            if (pod.getSpec() != null &&
                pod.getSpec().getContainers() != null &&
                !pod.getSpec().getContainers().isEmpty()) {

                // Get the first container (or loop through all if needed)
                V1Container container = pod.getSpec().getContainers().get(0);

                if (container.getResources() != null) {
                    // Get resource REQUESTS
                    if (container.getResources().getRequests() != null) {
                        Map<String, Quantity> requests = container.getResources().getRequests();

                        if (requests.containsKey("cpu")) {
                            resources.put("cpuRequest", requests.get("cpu").toSuffixedString());
                        }
                        if (requests.containsKey("memory")) {
                            resources.put("memoryRequest", requests.get("memory").toSuffixedString());
                        }
                    }

                    // Get resource LIMITS (optional, for future use)
                    if (container.getResources().getLimits() != null) {
                        Map<String, Quantity> limits = container.getResources().getLimits();

                        if (limits.containsKey("cpu")) {
                            resources.put("cpuLimit", limits.get("cpu").toSuffixedString());
                        }
                        if (limits.containsKey("memory")) {
                            resources.put("memoryLimit", limits.get("memory").toSuffixedString());
                        }
                    }
                }
            }

            logger.debug("Resource requests for pod {}/{}: {}", namespace, podName, resources);

        } catch (ApiException e) {
            logger.error("Failed to get resource requests for pod {}/{}: {}",
                    namespace, podName, e.getMessage());
            // Don't throw - just return empty map and let defaults be used
        }

        return resources;
    }

    /**
     * Get single pod info with reason, timestamp, container reasons
     */
    public PodInfo getPodInfo(String namespace, String podName) throws ApiException {
        try {
            V1Pod v1Pod = coreV1Api.readNamespacedPod(podName, namespace, null);
            return convertV1PodToPodInfo(v1Pod);
        } catch (ApiException e) {
            logger.error("Error fetching pod {}/{}: {}", namespace, podName, e.getMessage());
            throw e;
        }
    }

    /**
     * Check if pod exists
     */
    public boolean podExists(String namespace, String podName) throws ApiException {
        try {
            V1Pod pod = coreV1Api.readNamespacedPod(podName, namespace, null);
            return pod != null;
        } catch (ApiException e) {
            if (e.getCode() == 404) return false;
            throw e;
        }
    }

    /**
     * Convert Kubernetes V1Pod to our PodInfo (extracts reason, timestamp, container reasons)
     */
    private PodInfo convertV1PodToPodInfo(V1Pod v1Pod) {
    if (v1Pod == null || v1Pod.getMetadata() == null) return null;

    V1ObjectMeta metadata = v1Pod.getMetadata();
    V1PodStatus status = v1Pod.getStatus();
    V1PodSpec spec = v1Pod.getSpec();

    // Get container specs for image extraction
    Map<String, V1Container> containerSpecs = spec.getContainers().stream()
            .collect(Collectors.toMap(V1Container::getName, container -> container));

    // Extract containers with reasons
    List<ContainerInfo> containers = new ArrayList<>();
    if (status != null && status.getContainerStatuses() != null) {
        for (V1ContainerStatus containerStatus : status.getContainerStatuses()) {
            ContainerInfo containerInfo = new ContainerInfo();
            containerInfo.setName(containerStatus.getName());
            containerInfo.setReady(containerStatus.getReady() != null && containerStatus.getReady());
            containerInfo.setRestartCount(containerStatus.getRestartCount() != null ? containerStatus.getRestartCount() : 0);

            // Get image from spec
            V1Container containerSpec = containerSpecs.get(containerStatus.getName());
            if (containerSpec != null) {
                containerInfo.setImage(containerSpec.getImage());
            }

            // Extract container reason (OOMKilled, ImagePullBackOff, etc)
            if (containerStatus.getState() != null) {
                V1ContainerState state = containerStatus.getState();
                if (state.getRunning() != null) {
                    containerInfo.setState("Running");
                    containerInfo.setReason("Started");
                } else if (state.getWaiting() != null) {
                    containerInfo.setState("Waiting");
                    containerInfo.setReason(state.getWaiting().getReason());
                } else if (state.getTerminated() != null) {
                    containerInfo.setState("Terminated");
                    containerInfo.setReason(state.getTerminated().getReason());
                }
            }
            containers.add(containerInfo);
        }
    }

    // Get metrics
    Map<String, String> metrics = metricsService.getPodMetrics(
            metadata.getNamespace(),
            metadata.getName()
    );

    // Set creation timestamp for age checking
    LocalDateTime createdTime = LocalDateTime.now();
    if (metadata.getCreationTimestamp() != null) {
        createdTime = LocalDateTime.ofInstant(
                metadata.getCreationTimestamp().toInstant(),
                ZoneId.systemDefault()
        );
    }

    // Create and return PodInfo with all fields
    PodInfo podInfo = new PodInfo(
            metadata.getName(),
            metadata.getNamespace(),
            status != null ? status.getPhase() : "Unknown",
            spec.getNodeName(),
            status != null ? status.getHostIP() : "Unknown",
            containers,
            metrics
    );
    
    // Set the new fields
    podInfo.setReason(status != null ? status.getReason() : null);
    podInfo.setCreationTimestamp(createdTime);
    
    return podInfo;
}
    
}
