package com.example.demo.model.Cost;

import jakarta.persistence.*;


//
@Entity
@Table(name = "resource_costs")
public class ResourceCost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String podName;
    private String deploymentName;
    private double cpuRequest;      // in cores
    private double memoryRequest;   // in GB
    private double cpuUsage;        // actual usage
    private double memoryUsage;     // actual usage
    private double hourlyCost;
    private double monthlyCost;
    private double wastedCost;      // over-provisioned amount
    private String status;          // "efficient", "over-provisioned", "under-provisioned"

    public ResourceCost() {}

    public ResourceCost(String podName, double cpuRequest, double memoryRequest) {
        this.podName = podName;
        this.cpuRequest = cpuRequest;
        this.memoryRequest = memoryRequest;
    }

    // Getters and Setters
    public String getPodName() { return podName; }
    public void setPodName(String podName) { this.podName = podName; }

    public String getDeploymentName() { return deploymentName; }
    public void setDeploymentName(String deploymentName) { this.deploymentName = deploymentName; }

    public double getCpuRequest() { return cpuRequest; }
    public void setCpuRequest(double cpuRequest) { this.cpuRequest = cpuRequest; }

    public double getMemoryRequest() { return memoryRequest; }
    public void setMemoryRequest(double memoryRequest) { this.memoryRequest = memoryRequest; }

    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }

    public double getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }

    public double getHourlyCost() { return hourlyCost; }
    public void setHourlyCost(double hourlyCost) { this.hourlyCost = hourlyCost; }

    public double getMonthlyCost() { return monthlyCost; }
    public void setMonthlyCost(double monthlyCost) { this.monthlyCost = monthlyCost; }

    public double getWastedCost() { return wastedCost; }
    public void setWastedCost(double wastedCost) { this.wastedCost = wastedCost; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}