package com.example.demo.service.Cost;

import com.example.demo.dto.PodMetricsHistoryDTO;
import com.example.demo.model.Cost.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 🧠 INTELLIGENT COST RECOMMENDATIONS
 * 
 * Uses historical data to generate production-safe recommendations:
 * - Analyzes P95/P99 percentiles (not just averages)
 * - Considers workload patterns and variability
 * - Adds safety buffers based on spike behavior
 * - Only recommends changes when data is statistically significant
 */
@Service
public class SmartRecommendationEngine {
    private static final Logger logger = LoggerFactory.getLogger(SmartRecommendationEngine.class);

    @Autowired
    private CostHistoryService historyService;

    @Value("${cost.cpu.per.hour:0.031}")
    private double cpuCostPerHour;

    @Value("${cost.memory.gb.per.hour:0.004}")
    private double memoryCostPerHour;

    // Configuration
    private static final int MIN_SNAPSHOTS_REQUIRED = 20; // Need at least 20 data points
    private static final double MIN_SAVINGS_THRESHOLD = 1.0; // Only recommend if saves >$1/month
    private static final double SAFETY_BUFFER_STABLE = 1.3; // 30% buffer for stable workloads
    private static final double SAFETY_BUFFER_VARIABLE = 1.5; // 50% buffer for variable workloads
    private static final double SAFETY_BUFFER_SPIKY = 2.0; // 100% buffer for spiky workloads

    /**
     * Generate intelligent recommendations using historical data
     */
    /**
 * Generate intelligent recommendations using historical data (OPTIMIZED)
 */
public List<CostRecommendation> generateSmartRecommendations(String namespace, String podName) {
    List<CostRecommendation> recommendations = new ArrayList<>();

    try {
        // 🚀 OPTIMIZATION: Fetch only this pod's history from database
        // Instead of: Get all pods → filter in memory
        // Now: Database does the filtering with indexed queries
        List<PodMetricsHistoryDTO> podHistory = historyService.getPodHistory(namespace, podName, 7);
        
        if (podHistory.size() < MIN_SNAPSHOTS_REQUIRED) {
            logger.info("📊 Insufficient data for {} ({} snapshots, need {})", 
                podName, podHistory.size(), MIN_SNAPSHOTS_REQUIRED);
            return recommendations;
        }

        // Convert ResourceCost to ResourceMetrics (if needed)
        List<ResourceMetrics> metrics = podHistory.stream()
            .map(rc -> new ResourceMetrics(
                rc.getCpuRequest(),
                rc.getCpuUsage(),
                rc.getMemoryRequest(),
                rc.getMemoryUsage()
            ))
            .collect(Collectors.toList());

        // Get current resource requests (last snapshot)
        ResourceMetrics current = metrics.get(metrics.size() - 1);
        
        // Analyze CPU
        CostRecommendation cpuRec = analyzeCpuUsage(podName, metrics, current);
        if (cpuRec != null) {
            recommendations.add(cpuRec);
        }

        // Analyze Memory
        CostRecommendation memRec = analyzeMemoryUsage(podName, metrics, current);
        if (memRec != null) {
            recommendations.add(memRec);
        }

    } catch (Exception e) {
        logger.error("Error generating smart recommendations for {}: {}", podName, e.getMessage());
    }

    return recommendations;
}


    /**
     * Analyze CPU usage patterns and generate recommendation
     */
    private CostRecommendation analyzeCpuUsage(String podName, List<ResourceMetrics> history, 
                                                ResourceMetrics current) {
        List<Double> cpuUsages = history.stream()
            .map(ResourceMetrics::getCpuUsage)
            .filter(cpu -> cpu > 0) // Ignore zero values
            .collect(Collectors.toList());

        if (cpuUsages.isEmpty()) {
            return null; // No valid data
        }

        // Calculate statistics
        UsageStats stats = calculateStats(cpuUsages);
        double currentRequest = current.getCpuRequest();

        // Determine workload characteristics
        WorkloadPattern pattern = classifyWorkload(stats);
        double safetyBuffer = getSafetyBuffer(pattern);

        logger.info("📊 CPU Analysis for {}: P50={:.3f}, P95={:.3f}, P99={:.3f}, CV={:.2f}, Pattern={}", 
            podName, stats.p50, stats.p95, stats.p99, stats.coefficientOfVariation, pattern);

        // CASE 1: Over-provisioned (using < 50% of P95)
        if (currentRequest > stats.p95 * 2.0 && stats.p99 < currentRequest * 0.6) {
            // Use P99 with buffer, but minimum 20m for stability
            double recommended = Math.max(0.02, Math.max(stats.p99 * safetyBuffer, stats.p95 * 1.2));
            double savings = (currentRequest - recommended) * cpuCostPerHour * 24 * 30;

            if (savings > MIN_SAVINGS_THRESHOLD) {
                return new CostRecommendation(
                    podName,
                    "reduce_cpu",
                    formatCpu(currentRequest),
                    formatCpu(recommended),
                    savings,
                    String.format(
                        "CPU over-provisioned: P95 usage is %.0fm (%.1f%% of request). " +
                        "Workload is %s. Recommending P99 (%.0fm) × %.1fx safety buffer.",
                        stats.p95 * 1000,
                        (stats.p95 / currentRequest) * 100,
                        pattern.name().toLowerCase(),
                        stats.p99 * 1000,
                        safetyBuffer
                    )
                );
            }
        }

        // CASE 2: Under-provisioned (P95 > 80% of request)
        if (stats.p95 > currentRequest * 0.8) {
            double usagePercent = (stats.p95 / currentRequest) * 100;
            double recommended = stats.p99 * safetyBuffer;
            
            String priority = stats.max > currentRequest ? "critical" : 
                            usagePercent > 95 ? "high" : "medium";

            CostRecommendation rec = new CostRecommendation(
                podName,
                "increase_cpu",
                formatCpu(currentRequest),
                formatCpu(recommended),
                0, // Not a cost saving
                String.format(
                    "⚠️ CPU under-provisioned: P95 usage is %.0fm (%.1f%% of limit). " +
                    "%s workload detected. Risk of throttling!",
                    stats.p95 * 1000,
                    usagePercent,
                    pattern.name()
                )
            );
            rec.setPriority(priority);
            return rec;
        }

        // CASE 3: Efficient - no recommendation
        return null;
    }

    /**
     * Analyze Memory usage patterns and generate recommendation
     */
    private CostRecommendation analyzeMemoryUsage(String podName, List<ResourceMetrics> history,
                                                    ResourceMetrics current) {
        List<Double> memUsages = history.stream()
            .map(ResourceMetrics::getMemoryUsage)
            .filter(mem -> mem > 0)
            .collect(Collectors.toList());

        if (memUsages.isEmpty()) {
            return null;
        }

        UsageStats stats = calculateStats(memUsages);
        double currentRequest = current.getMemoryRequest();
        WorkloadPattern pattern = classifyWorkload(stats);
        double safetyBuffer = getSafetyBuffer(pattern);

        logger.info("📊 Memory Analysis for {}: P50={:.2f}GB, P95={:.2f}GB, P99={:.2f}GB, Pattern={}", 
            podName, stats.p50, stats.p95, stats.p99, pattern);

        // CASE 1: Over-provisioned (using < 65% of P95)
        if (currentRequest > stats.p95 * 1.5 && stats.p99 < currentRequest * 0.7) {
            double recommended = Math.max(0.0625, stats.p99 * safetyBuffer); // Min 64Mi
            double savings = (currentRequest - recommended) * memoryCostPerHour * 24 * 30;

            if (savings > MIN_SAVINGS_THRESHOLD) {
                return new CostRecommendation(
                    podName,
                    "reduce_memory",
                    formatMemory(currentRequest),
                    formatMemory(recommended),
                    savings,
                    String.format(
                        "Memory over-provisioned: P95 usage is %.0fMi (%.1f%% of request). " +
                        "Workload is %s. Recommending P99 (%.0fMi) × %.1fx buffer.",
                        stats.p95 * 1024,
                        (stats.p95 / currentRequest) * 100,
                        pattern.name().toLowerCase(),
                        stats.p99 * 1024,
                        safetyBuffer
                    )
                );
            }
        }

        // CASE 2: Under-provisioned (P95 > 85% of request) - CRITICAL FOR MEMORY!
        if (stats.p95 > currentRequest * 0.85) {
            double usagePercent = (stats.p95 / currentRequest) * 100;
            double recommended = stats.p99 * Math.max(safetyBuffer, 1.3); // Extra buffer for memory
            
            String priority = stats.max > currentRequest ? "critical" : 
                            usagePercent > 95 ? "high" : "medium";

            CostRecommendation rec = new CostRecommendation(
                podName,
                "increase_memory",
                formatMemory(currentRequest),
                formatMemory(recommended),
                0,
                String.format(
                    "⚠️ Memory under-provisioned: P95 usage is %.0fMi (%.1f%% of limit). " +
                    "HIGH RISK of OOMKill! %s workload.",
                    stats.p95 * 1024,
                    usagePercent,
                    pattern.name()
                )
            );
            rec.setPriority(priority);
            return rec;
        }

        return null;
    }

    /**
     * Calculate statistical measures from usage data
     */
    private UsageStats calculateStats(List<Double> values) {
        if (values.isEmpty()) {
            return new UsageStats(0, 0, 0, 0, 0, 0, 0);
        }

        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        
        double avg = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double min = sorted.get(0);
        double max = sorted.get(sorted.size() - 1);
        double p50 = percentile(sorted, 50);
        double p95 = percentile(sorted, 95);
        double p99 = percentile(sorted, 99);
        
        // Calculate coefficient of variation (std dev / mean)
        double variance = sorted.stream()
            .mapToDouble(v -> Math.pow(v - avg, 2))
            .average()
            .orElse(0);
        double stdDev = Math.sqrt(variance);
        double cv = avg > 0 ? stdDev / avg : 0;

        return new UsageStats(avg, min, max, p50, p95, p99, cv);
    }

    /**
     * Classify workload pattern based on usage variability
     */
    private WorkloadPattern classifyWorkload(UsageStats stats) {
        // Coefficient of Variation analysis:
        // CV < 0.2: Very stable (batch jobs, steady services)
        // CV 0.2-0.5: Normal variability (typical web apps)
        // CV > 0.5: High variability (bursty, event-driven)
        
        if (stats.coefficientOfVariation > 0.5) {
            return WorkloadPattern.SPIKY;
        } else if (stats.coefficientOfVariation > 0.2) {
            return WorkloadPattern.VARIABLE;
        } else {
            return WorkloadPattern.STABLE;
        }
    }

    /**
     * Get safety buffer based on workload pattern
     */
    private double getSafetyBuffer(WorkloadPattern pattern) {
        switch (pattern) {
            case STABLE: return SAFETY_BUFFER_STABLE; // 1.3x
            case VARIABLE: return SAFETY_BUFFER_VARIABLE; // 1.5x
            case SPIKY: return SAFETY_BUFFER_SPIKY; // 2.0x
            default: return SAFETY_BUFFER_VARIABLE;
        }
    }

    /**
     * Calculate percentile from sorted list
     */
    private double percentile(List<Double> sorted, int percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    /**
     * Extract pod metrics from snapshots
     */
    /* 
    private List<ResourceMetrics> extractPodMetrics(List<ResourceCost> podHistory) {
        List<ResourceMetrics> metrics = new ArrayList<>();
        
        for (CostSnapshot snapshot : history) {
            for (ResourceCost rc : snapshot.getPodCosts()) {
                if (rc.getPodName().equals(podName)) {
                    metrics.add(new ResourceMetrics(
                        rc.getCpuRequest(),
                        rc.getCpuUsage(),
                        rc.getMemoryRequest(),
                        rc.getMemoryUsage()
                    ));
                    break;
                }
            }
        }
        
        return metrics;
    }  */
      // this above method is not needed anymore due to optimization in data fetching

    // Formatting helpers
    private String formatCpu(double cores) {
        return String.format("CPU: %.3f cores (%.0fm)", cores, cores * 1000);
    }

    private String formatMemory(double gb) {
        return String.format("Memory: %.2f GB (%.0fMi)", gb, gb * 1024);
    }

    // Inner classes for data structures
    private static class UsageStats {
        double avg, min, max, p50, p95, p99, coefficientOfVariation;
        
        UsageStats(double avg, double min, double max, double p50, double p95, double p99, double cv) {
            this.avg = avg;
            this.min = min;
            this.max = max;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
            this.coefficientOfVariation = cv;
        }
    }

    private static class ResourceMetrics {
        double cpuRequest, cpuUsage, memoryRequest, memoryUsage;
        
        ResourceMetrics(double cpuReq, double cpuUse, double memReq, double memUse) {
            this.cpuRequest = cpuReq;
            this.cpuUsage = cpuUse;
            this.memoryRequest = memReq;
            this.memoryUsage = memUse;
        }

        double getCpuRequest() { return cpuRequest; }
        double getCpuUsage() { return cpuUsage; }
        double getMemoryRequest() { return memoryRequest; }
        double getMemoryUsage() { return memoryUsage; }
    }

    private enum WorkloadPattern {
        STABLE,    // Low variability (CV < 0.2)
        VARIABLE,  // Normal variability (CV 0.2-0.5)
        SPIKY      // High variability (CV > 0.5)
    }
}