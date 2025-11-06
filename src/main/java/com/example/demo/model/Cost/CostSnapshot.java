package com.example.demo.model.Cost;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 *  COST SNAPSHOT - Point-in-time cost data
 * Stores historical cost information for trend analysis
 */
@Entity
@Table(name = "cost_snapshots")
public class CostSnapshot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String namespace;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    // Aggregated costs
    private double totalMonthlyCost;
    private double totalHourlyCost;
    private double totalCpuCores;
    private double totalMemoryGb;
    private int totalPods;
    private double efficiencyScore;
    
    //  Store individual pod costs as JSON
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "snapshot_id")
    private List<ResourceCost> podCosts = new ArrayList<>();
    
    // Constructors
    public CostSnapshot() {
        this.timestamp = LocalDateTime.now();
    }
    
    public CostSnapshot(String namespace) {
        this.namespace = namespace;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
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
    
    public double getTotalHourlyCost() {
        return totalHourlyCost;
    }
    
    public void setTotalHourlyCost(double totalHourlyCost) {
        this.totalHourlyCost = totalHourlyCost;
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
    
    public int getTotalPods() {
        return totalPods;
    }
    
    public void setTotalPods(int totalPods) {
        this.totalPods = totalPods;
    }
    
    public double getEfficiencyScore() {
        return efficiencyScore;
    }
    
    public void setEfficiencyScore(double efficiencyScore) {
        this.efficiencyScore = efficiencyScore;
    }
    
    // ✅ ADD THESE GETTERS/SETTERS
    public List<ResourceCost> getPodCosts() {
        return podCosts;
    }
    
    public void setPodCosts(List<ResourceCost> podCosts) {
        this.podCosts = podCosts;
    }
    
    @Override
    public String toString() {
        return String.format("CostSnapshot[namespace=%s, timestamp=%s, cost=$%.2f/month, pods=%d, efficiency=%.1f%%]",
            namespace, timestamp, totalMonthlyCost, totalPods, efficiencyScore);
    }
}