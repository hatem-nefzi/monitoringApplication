package com.example.demo.model.Cost;

public class CostRecommendation {
    private String podName;
    private String type;              // "reduce_cpu", "reduce_memory", "rightsizing"
    private String currentConfig;     // e.g., "CPU: 2 cores, Memory: 4GB"
    private String recommendedConfig; // e.g., "CPU: 0.5 cores, Memory: 1GB"
    private double potentialSavings;  // monthly savings in $
    private String reason;
    private String priority;          // "high", "medium", "low"

    public CostRecommendation() {}

    public CostRecommendation(String podName, String type, String currentConfig, 
                              String recommendedConfig, double potentialSavings, String reason) {
        this.podName = podName;
        this.type = type;
        this.currentConfig = currentConfig;
        this.recommendedConfig = recommendedConfig;
        this.potentialSavings = potentialSavings;
        this.reason = reason;
        this.priority = potentialSavings > 50 ? "high" : potentialSavings > 20 ? "medium" : "low";
    }

    // Getters and Setters
    public String getPodName() { return podName; }
    public void setPodName(String podName) { this.podName = podName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCurrentConfig() { return currentConfig; }
    public void setCurrentConfig(String currentConfig) { this.currentConfig = currentConfig; }

    public String getRecommendedConfig() { return recommendedConfig; }
    public void setRecommendedConfig(String recommendedConfig) { 
        this.recommendedConfig = recommendedConfig; 
    }

    public double getPotentialSavings() { return potentialSavings; }
    public void setPotentialSavings(double potentialSavings) { 
        this.potentialSavings = potentialSavings; 
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}