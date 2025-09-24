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
    private String creationTimestamp; // Add this line

    public DeploymentInfo(V1Deployment deployment) {
        this.name = deployment.getMetadata() != null ? deployment.getMetadata().getName() : "unknown";
        this.namespace = deployment.getMetadata() != null ? deployment.getMetadata().getNamespace() : "unknown";

        if (deployment.getSpec() != null && deployment.getSpec().getReplicas() != null) {
            this.replicas = deployment.getSpec().getReplicas();
        } else {
            this.replicas = 0;
        }

        if (deployment.getStatus() != null && deployment.getStatus().getAvailableReplicas() != null) {
            this.availableReplicas = deployment.getStatus().getAvailableReplicas();
        } else {
            this.availableReplicas = 0;
        }

        this.labels = deployment.getMetadata() != null ? deployment.getMetadata().getLabels() : null;
        this.creationTimestamp = deployment.getMetadata().getCreationTimestamp().toString(); // Add this line
    }
}
