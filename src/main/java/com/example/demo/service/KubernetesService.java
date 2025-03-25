package com.example.demo.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1PodList;

import com.example.demo.model.ContainerInfo;
import com.example.demo.model.PodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
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
            
            List<PodInfo> podInfos = podList.getItems().stream()
                .map(pod -> {
                    List<ContainerInfo> containers = pod.getSpec().getContainers().stream()
                        .map(container -> new ContainerInfo(
                            container.getName(),
                            container.getImage()
                        ))
                        .collect(Collectors.toList());
                    
                    PodInfo info = new PodInfo(
                        pod.getMetadata().getName(),
                        pod.getMetadata().getNamespace(),
                        pod.getStatus().getPhase(),
                        containers
                    );
                    logger.trace("Processed pod with {} containers: {}", containers.size(), info);
                    return info;
                })
                .collect(Collectors.toList());
            
            logger.info("Found {} pods in namespace {}", podInfos.size(), namespace == null ? "all" : namespace);
            return podInfos;
            
        } catch (ApiException e) {
            logger.error("Failed to fetch pods for namespace {}: {}", namespace, e.getResponseBody(), e);
            throw e;
        }
    }
}