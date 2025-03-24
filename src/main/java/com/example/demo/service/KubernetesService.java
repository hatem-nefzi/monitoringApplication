package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.client.KubernetesClient;

@Service
public class KubernetesService {
    private final KubernetesClient kubernetesClient;

    public KubernetesService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public List<PodInfo> getPods() {
        return kubernetesClient.pods().list().getItems().stream()
            .map(pod -> new PodInfo(pod.getMetadata().getName(), pod.getStatus().getPhase()))
            .collect(Collectors.toList());
    }

    public List<DeploymentInfo> getDeployments() {
        return kubernetesClient.apps().deployments().list().getItems().stream()
            .map(deployment -> new DeploymentInfo(deployment.getMetadata().getName(), deployment.getStatus().getReplicas()))
            .collect(Collectors.toList());
    }
}
