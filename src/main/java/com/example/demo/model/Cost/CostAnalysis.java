package com.example.demo.model.Cost;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class CostAnalysis {
    private String namespace;
    private double monthlyCost;
    private double hourlyCost;
    private int totalPods;
    private double totalCpuCores;
    private double totalMemoryGb;
    private double efficiencyScore; //0 to 100
    private List<ResourceCost> podCosts;
    private List<CostRecommendation> recommendations;
    private LocalDateTime timestamp;

    public CostAnalysis() {
        this.timestamp = LocalDateTime.now();
    }
    
    public CostAnalysis(String namespace, double hourlyCost, int totalPods) {
        this();
        this.namespace = namespace;
        this.hourlyCost = hourlyCost;
        this.monthlyCost = hourlyCost * 24 * 30; // Approximate monthly
        this.totalPods = totalPods;
    }
    // Getters and Setters
    public String getNamespace() {
        return namespace;
    }
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public double getMonthlyCost() { return monthlyCost; }
    public void setMonthlyCost(double monthlyCost) { this.monthlyCost = monthlyCost; }

    public double getHourlyCost() { return hourlyCost; }
    public void setHourlyCost(double hourlyCost) { this.hourlyCost = hourlyCost; }

    public int getTotalPods() { return totalPods; }
    public void setTotalPods(int totalPods) { this.totalPods = totalPods; }

    public double getTotalCpuCores() { return totalCpuCores; }
    public void setTotalCpuCores(double totalCpuCores) { this.totalCpuCores = totalCpuCores; }

    public double getTotalMemoryGb() { return totalMemoryGb; }
    public void setTotalMemoryGb(double totalMemoryGb) { this.totalMemoryGb = totalMemoryGb; }

    public double getEfficiencyScore() { return efficiencyScore; }
    public void setEfficiencyScore(double efficiencyScore) { this.efficiencyScore = efficiencyScore; }

    public List<ResourceCost> getPodCosts() { return podCosts; }
    public void setPodCosts(List<ResourceCost> podCosts) { this.podCosts = podCosts; }

    public List<CostRecommendation> getRecommendations() { return recommendations; }
    public void setRecommendations(List<CostRecommendation> recommendations) { 
        this.recommendations = recommendations; 
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

}
