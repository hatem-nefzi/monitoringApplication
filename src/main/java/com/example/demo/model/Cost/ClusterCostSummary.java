package com.example.demo.model.Cost;

import java.util.Map;

public class ClusterCostSummary {
    private double totalMonthlyCost;
    private double totalWastedCost;
    private double potentialSavings;
    private int totalPods;
    private int efficientPods;
    private int overProvisionedPods;
    private double averageEfficiencyScore;
    private Map<String, Double> costByNamespace;
    private String mostExpensiveNamespace;
    private String leastEfficientNamespace;

    public ClusterCostSummary() {}

    // Getters and Setters
    public double getTotalMonthlyCost() { return totalMonthlyCost; }
    public void setTotalMonthlyCost(double totalMonthlyCost) { 
        this.totalMonthlyCost = totalMonthlyCost; 
    }

    public double getTotalWastedCost() { return totalWastedCost; }
    public void setTotalWastedCost(double totalWastedCost) { 
        this.totalWastedCost = totalWastedCost; 
    }

    public double getPotentialSavings() { return potentialSavings; }
    public void setPotentialSavings(double potentialSavings) { 
        this.potentialSavings = potentialSavings; 
    }

    public int getTotalPods() { return totalPods; }
    public void setTotalPods(int totalPods) { this.totalPods = totalPods; }

    public int getEfficientPods() { return efficientPods; }
    public void setEfficientPods(int efficientPods) { this.efficientPods = efficientPods; }

    public int getOverProvisionedPods() { return overProvisionedPods; }
    public void setOverProvisionedPods(int overProvisionedPods) { 
        this.overProvisionedPods = overProvisionedPods; 
    }

    public double getAverageEfficiencyScore() { return averageEfficiencyScore; }
    public void setAverageEfficiencyScore(double averageEfficiencyScore) { 
        this.averageEfficiencyScore = averageEfficiencyScore; 
    }

    public Map<String, Double> getCostByNamespace() { return costByNamespace; }
    public void setCostByNamespace(Map<String, Double> costByNamespace) { 
        this.costByNamespace = costByNamespace; 
    }

    public String getMostExpensiveNamespace() { return mostExpensiveNamespace; }
    public void setMostExpensiveNamespace(String mostExpensiveNamespace) { 
        this.mostExpensiveNamespace = mostExpensiveNamespace; 
    }

    public String getLeastEfficientNamespace() { return leastEfficientNamespace; }
    public void setLeastEfficientNamespace(String leastEfficientNamespace) { 
        this.leastEfficientNamespace = leastEfficientNamespace; 
    }
}