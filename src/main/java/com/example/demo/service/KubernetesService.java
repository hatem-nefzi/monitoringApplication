package com.example.demo.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1PodList;
import java.util.List;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.model.PodInfo;

@Service
public class KubernetesService {

    private final CoreV1Api coreV1Api;

    @Autowired
    public KubernetesService(ApiClient apiClient) {
        this.coreV1Api = new CoreV1Api(apiClient);
    }
    public List<String> getPodNames() throws ApiException {
        V1PodList podList = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
        return podList.getItems().stream()
            .map(pod -> pod.getMetadata().getName())
            .collect(Collectors.toList());
    }

    public List<PodInfo> getPodInfo(String namespace) throws ApiException {
        V1PodList podList = namespace == null ?
            coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null) :
            coreV1Api.listNamespacedPod(namespace, null, null, null, null, null, null, null, null, null, null);
        
        return podList.getItems().stream()
            .map(pod -> new PodInfo(
                pod.getMetadata().getName(),
                pod.getMetadata().getNamespace(),
                pod.getStatus().getPhase()
            ))
            .collect(Collectors.toList());
    }
}