package com.example.demo.model;




import io.kubernetes.client.openapi.models.V1Deployment;
import lombok.Data;

import java.util.Map;

@Data
public class DeploymentInfo {
    private String name;
    private String namespace;
    private int replicas;
    private int availableReplicas;
    private Map<String, String> labels;

    public DeploymentInfo(V1Deployment deployment) {
        this.name = deployment.getMetadata().getName();
        this.namespace = deployment.getMetadata().getNamespace();
        this.replicas = deployment.getSpec().getReplicas();
        this.availableReplicas = deployment.getStatus().getAvailableReplicas();
        this.labels = deployment.getMetadata().getLabels();
    }
}