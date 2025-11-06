package com.example.demo.dto;

import java.time.LocalDateTime;

public class PodMetricsHistoryDTO {
    private String podName;
    private double cpuRequest;
    private double cpuUsage;
    private double memoryRequest;
    private double memoryUsage;
    private LocalDateTime timestamp;

    public PodMetricsHistoryDTO(String podName, double cpuRequest, double cpuUsage, double memoryRequest, double memoryUsage, LocalDateTime timestamp) {
        this.podName = podName;
        this.cpuRequest = cpuRequest;
        this.cpuUsage = cpuUsage;
        this.memoryRequest = memoryRequest;
        this.memoryUsage = memoryUsage;
        this.timestamp = timestamp;
    }

    // Getters...
    public String getPodName() {
        return podName;
    }

    public double getCpuRequest() {
        return cpuRequest;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public double getMemoryRequest() {
        return memoryRequest;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
}
