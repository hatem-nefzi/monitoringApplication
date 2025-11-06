// src/main/java/com/example/demo/service/Cost/CostHistoryService.java
package com.example.demo.service.Cost;

import com.example.demo.model.Cost.CostSnapshot;
import com.example.demo.model.Cost.ResourceCost;
import com.example.demo.repository.CostSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 📊 COST HISTORY & ANALYTICS SERVICE
 * 
 * Handles historical data retrieval, trend analysis, and cleanup.
 * Does NOT create snapshots - that's CostSnapshotService's job.
 * This breaks the circular dependency.
 */
@Service
public class CostHistoryService {
    private static final Logger logger = LoggerFactory.getLogger(CostHistoryService.class);
    
    @Autowired
    private CostSnapshotRepository snapshotRepository;
    
    /**
     * Get cost history for a namespace (last N days)
     */
    public List<CostSnapshot> getCostHistory(String namespace, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return snapshotRepository.findRecentByNamespace(namespace, since);
    }
    
    /**
     * Get all cost history for a namespace
     */
    public List<CostSnapshot> getAllHistory(String namespace) {
        return snapshotRepository.findByNamespaceOrderByTimestampDesc(namespace);
    }
    
    /**
     * Calculate savings compared to baseline (first snapshot)
     */
    public Map<String, Object> calculateSavings(String namespace) {
        logger.info("💰 Calculating savings for namespace: {}", namespace);
        
        // Get oldest and newest snapshots
        Optional<CostSnapshot> baselineOpt = snapshotRepository
            .findFirstByNamespaceOrderByTimestampAsc(namespace);
        Optional<CostSnapshot> currentOpt = snapshotRepository
            .findFirstByNamespaceOrderByTimestampDesc(namespace);
        
        if (baselineOpt.isEmpty() || currentOpt.isEmpty()) {
            logger.warn("⚠️ Not enough data to calculate savings for {}", namespace);
            return Map.of(
                "hasData", false,
                "message", "Not enough historical data. Need at least 2 snapshots."
            );
        }
        
        CostSnapshot baseline = baselineOpt.get();
        CostSnapshot current = currentOpt.get();
        
        double baselineCost = baseline.getTotalMonthlyCost();
        double currentCost = current.getTotalMonthlyCost();
        double savedMonthly = baselineCost - currentCost;
        double savedAnnually = savedMonthly * 12;
        double percentReduction = baselineCost > 0 
            ? (savedMonthly / baselineCost) * 100 
            : 0;
        
        // Calculate efficiency improvement
        double efficiencyImprovement = current.getEfficiencyScore() - baseline.getEfficiencyScore();
        
        Map<String, Object> savings = new HashMap<>();
        savings.put("hasData", true);
        savings.put("baselineCost", baselineCost);
        savings.put("currentCost", currentCost);
        savings.put("savedMonthly", savedMonthly);
        savings.put("savedAnnually", savedAnnually);
        savings.put("percentReduction", percentReduction);
        savings.put("baselineTimestamp", baseline.getTimestamp());
        savings.put("currentTimestamp", current.getTimestamp());
        savings.put("baselineEfficiency", baseline.getEfficiencyScore());
        savings.put("currentEfficiency", current.getEfficiencyScore());
        savings.put("efficiencyImprovement", efficiencyImprovement);
        savings.put("daysTracked", calculateDaysBetween(baseline.getTimestamp(), current.getTimestamp()));
        
        logger.info("✅ Savings calculated: ${}/month saved ({}% reduction)", 
            String.format("%.2f", savedMonthly), 
            String.format("%.1f", percentReduction));
        
        return savings;
    }
    
    /**
     * Get cost trend analysis (increasing, decreasing, stable)
     */
    public Map<String, Object> analyzeTrend(String namespace, int days) {
        List<CostSnapshot> history = getCostHistory(namespace, days);
        
        if (history.size() < 2) {
            return Map.of(
                "trend", "insufficient_data",
                "message", "Need at least 2 data points"
            );
        }
        
        // Calculate average cost change
        double totalChange = 0;
        for (int i = 1; i < history.size(); i++) {
            double change = history.get(i).getTotalMonthlyCost() 
                          - history.get(i-1).getTotalMonthlyCost();
            totalChange += change;
        }
        
        double avgChange = totalChange / (history.size() - 1);
        
        String trend;
        String emoji;
        if (avgChange < -5) {
            trend = "decreasing";
            emoji = "📉";
        } else if (avgChange > 5) {
            trend = "increasing";
            emoji = "📈";
        } else {
            trend = "stable";
            emoji = "➡️";
        }
        
        return Map.of(
            "trend", trend,
            "emoji", emoji,
            "avgDailyChange", avgChange / days,
            "totalChange", totalChange,
            "dataPoints", history.size(),
            "firstCost", history.get(0).getTotalMonthlyCost(),
            "lastCost", history.get(history.size() - 1).getTotalMonthlyCost()
        );
    }
    
    /**
     * Cleanup old snapshots (keep last 90 days)
     */
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM daily
    @Transactional
    public void cleanupOldSnapshots() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        logger.info("🧹 Cleaning up snapshots older than {}", cutoff);
        
        snapshotRepository.deleteByTimestampBefore(cutoff);
        
        logger.info("✅ Cleanup complete");
    }
    
    /**
     * Helper: Calculate days between two timestamps
     */
    private long calculateDaysBetween(LocalDateTime start, LocalDateTime end) {
        return java.time.Duration.between(start, end).toDays();
    }
    
    /**
     * Get cluster-wide cost history
     */
    public Map<String, Object> getClusterCostHistory(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<CostSnapshot> allSnapshots = snapshotRepository
            .findByTimestampBetweenOrderByTimestampAsc(since, LocalDateTime.now());
        
        // Group by date and sum costs
        Map<String, Double> dailyCosts = allSnapshots.stream()
            .collect(Collectors.groupingBy(
                snapshot -> snapshot.getTimestamp().toLocalDate().toString(),
                Collectors.summingDouble(CostSnapshot::getTotalMonthlyCost)
            ));
        
        return Map.of(
            "dailyCosts", dailyCosts,
            "dataPoints", dailyCosts.size(),
            "totalSnapshots", allSnapshots.size()
        );
    }
    /**
 * Get historical metrics for a specific pod (optimized)
 */
public List<ResourceCost> getPodHistory(String namespace, String podName, int days) {
    LocalDateTime since = LocalDateTime.now().minusDays(days);
    return snapshotRepository.findPodMetricsHistory(namespace, podName, since);
} 
}