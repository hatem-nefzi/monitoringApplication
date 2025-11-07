package com.example.demo.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class PodInfo {
    private String name;
    private String namespace;
    private String status;
    private String nodeName;
    private String nodeIP;
    private List<ContainerInfo> containers;
    private Map<String, String> metrics;  // New field
    // ✅ ADD THIS - Default constructor for Jackson
    private String reason;  // Evicted, Terminating, etc
    private LocalDateTime creationTimestamp;  // When was pod created

    public PodInfo() {
    }

    public PodInfo(String name, String namespace, String status, 
                 String nodeName, String nodeIP, List<ContainerInfo> containers, Map<String, String> metrics) {
        this.name = name;
        this.namespace = namespace;
        this.status = status;
        this.nodeName = nodeName;
        this.nodeIP = nodeIP;
        this.containers = containers;
        this.metrics = metrics;
    }
    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreationTimestamp() {
        return creationTimestamp;
    }

    public void setCreationTimestamp(LocalDateTime creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    @JsonIgnore
    public String getIdentifier() {
        return namespace + "/" + name;
    }
    
}