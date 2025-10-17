// src/main/java/com/example/demo/model/Cost/CostSnapshot.java
package com.example.demo.model.Cost;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity to store historical cost data for tracking trends over time
 */
@Entity
@Table(name = "cost_snapshots", indexes = {
    @Index(name = "idx_namespace_timestamp", columnList = "namespace,timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class CostSnapshot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String namespace;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false)
    private double totalMonthlyCost;
    
    @Column(nullable = false)
    private double hourlyCost;
    
    @Column(nullable = false)
    private double efficiencyScore;
    
    @Column(nullable = false)
    private int totalPods;
    
    @Column(nullable = false)
    private int efficientPods;
    
    @Column(nullable = false)
    private int overProvisionedPods;
    
    @Column(nullable = false)
    private int underProvisionedPods;
    
    @Column(nullable = false)
    private double totalCpuCores;
    
    @Column(nullable = false)
    private double totalMemoryGb;
    
    @Column(nullable = false)
    private double wastedCost;
    
    @Column(nullable = false)
    private double potentialSavings;
    
    // Constructors
    public CostSnapshot() {
    }
    
    public CostSnapshot(String namespace, CostAnalysis analysis) {
        this.namespace = namespace;
        this.timestamp = LocalDateTime.now();
        this.totalMonthlyCost = analysis.getMonthlyCost();
        this.hourlyCost = analysis.getHourlyCost();
        this.efficiencyScore = analysis.getEfficiencyScore();
        this.totalPods = analysis.getTotalPods();
        this.totalCpuCores = analysis.getTotalCpuCores();
        this.totalMemoryGb = analysis.getTotalMemoryGb();
        
        // Calculate pod statuses
        this.efficientPods = (int) analysis.getPodCosts().stream()
            .filter(pc -> "efficient".equals(pc.getStatus()))
            .count();
        
        this.overProvisionedPods = (int) analysis.getPodCosts().stream()
            .filter(pc -> "over-provisioned".equals(pc.getStatus()))
            .count();
        
        this.underProvisionedPods = (int) analysis.getPodCosts().stream()
            .filter(pc -> "under-provisioned".equals(pc.getStatus()))
            .count();
        
        // Calculate waste
        this.wastedCost = analysis.getPodCosts().stream()
            .mapToDouble(ResourceCost::getWastedCost)
            .sum();
        
        this.potentialSavings = analysis.getRecommendations().stream()
            .mapToDouble(CostRecommendation::getPotentialSavings)
            .sum();
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public double getTotalMonthlyCost() {
        return totalMonthlyCost;
    }
    
    public void setTotalMonthlyCost(double totalMonthlyCost) {
        this.totalMonthlyCost = totalMonthlyCost;
    }
    
    public double getHourlyCost() {
        return hourlyCost;
    }
    
    public void setHourlyCost(double hourlyCost) {
        this.hourlyCost = hourlyCost;
    }
    
    public double getEfficiencyScore() {
        return efficiencyScore;
    }
    
    public void setEfficiencyScore(double efficiencyScore) {
        this.efficiencyScore = efficiencyScore;
    }
    
    public int getTotalPods() {
        return totalPods;
    }
    
    public void setTotalPods(int totalPods) {
        this.totalPods = totalPods;
    }
    
    public int getEfficientPods() {
        return efficientPods;
    }
    
    public void setEfficientPods(int efficientPods) {
        this.efficientPods = efficientPods;
    }
    
    public int getOverProvisionedPods() {
        return overProvisionedPods;
    }
    
    public void setOverProvisionedPods(int overProvisionedPods) {
        this.overProvisionedPods = overProvisionedPods;
    }
    
    public int getUnderProvisionedPods() {
        return underProvisionedPods;
    }
    
    public void setUnderProvisionedPods(int underProvisionedPods) {
        this.underProvisionedPods = underProvisionedPods;
    }
    
    public double getTotalCpuCores() {
        return totalCpuCores;
    }
    
    public void setTotalCpuCores(double totalCpuCores) {
        this.totalCpuCores = totalCpuCores;
    }
    
    public double getTotalMemoryGb() {
        return totalMemoryGb;
    }
    
    public void setTotalMemoryGb(double totalMemoryGb) {
        this.totalMemoryGb = totalMemoryGb;
    }
    
    public double getWastedCost() {
        return wastedCost;
    }
    
    public void setWastedCost(double wastedCost) {
        this.wastedCost = wastedCost;
    }
    
    public double getPotentialSavings() {
        return potentialSavings;
    }
    
    public void setPotentialSavings(double potentialSavings) {
        this.potentialSavings = potentialSavings;
    }
}