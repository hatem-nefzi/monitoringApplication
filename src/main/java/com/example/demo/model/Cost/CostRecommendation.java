package com.example.demo.model.Cost;

import com.fasterxml.jackson.annotation.JsonProperty;


public class CostRecommendation {
    private String podName;
    
    @JsonProperty("type")  //  Map internal field to JSON "type"
    private String action;  
    
    @JsonProperty("currentConfig")
    private String currentValue;
    
    @JsonProperty("recommendedConfig")
    private String recommendedValue;
    
    private double potentialSavings;
    private String reason;
    private String priority;
    // default constructor was causing the cache read failed oh my goooooooooooood!!!!!!!!!!!!!!!!!
    public CostRecommendation() {
    }

    // Constructor
    public CostRecommendation(String podName, String action, String currentValue, 
                              String recommendedValue, double potentialSavings, String reason) {
        this.podName = podName;
        this.action = action;
        this.currentValue = currentValue;
        this.recommendedValue = recommendedValue;
        this.potentialSavings = potentialSavings;
        this.reason = reason;
        
        // Set default priority based on savings
        if (action.startsWith("increase")) {
            this.priority = "high";
        } else if (potentialSavings > 10) {
            this.priority = "high";
        } else if (potentialSavings > 5) {
            this.priority = "medium";
        } else {
            this.priority = "low";
        }
    }

    // Getters and Setters
    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
    }

    public String getRecommendedValue() {
        return recommendedValue;
    }

    public void setRecommendedValue(String recommendedValue) {
        this.recommendedValue = recommendedValue;
    }

    public double getPotentialSavings() {
        return potentialSavings;
    }

    public void setPotentialSavings(double potentialSavings) {
        this.potentialSavings = potentialSavings;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return String.format("Recommendation[pod=%s, action=%s, savings=$%.2f/month, priority=%s]",
            podName, action, potentialSavings, priority);
    }
}