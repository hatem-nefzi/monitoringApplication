package com.example.demo.service.Cost;

import com.example.demo.model.Cost.CostAnomaly;
import com.example.demo.model.Cost.CostSnapshot;
import com.example.demo.model.Cost.ResourceCost;
import com.example.demo.repository.CostSnapshotRepository;
import io.kubernetes.client.openapi.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 *  COST ANOMALY DETECTOR
 * Detects unusual cost patterns and spikes
 */
@Service
public class CostAnomalyDetector {
    private static final Logger logger = LoggerFactory.getLogger(CostAnomalyDetector.class);

    @Autowired
    private CostSnapshotRepository snapshotRepository;

    @Autowired
    private CostAnalysisService costAnalysisService;

    // Thresholds
    private static final double SPIKE_THRESHOLD = 30.0; // 30% increase
    private static final double DROP_THRESHOLD = 30.0; // 30% decrease
    private static final double CRITICAL_THRESHOLD = 50.0; // 50% change
    private static final int BASELINE_DAYS = 7; // Use last 7 days as baseline

    /**
     * Detect anomalies for a specific namespace
     */
    public List<CostAnomaly> detectAnomalies(String namespace) {
        logger.info(" Detecting cost anomalies for namespace: {}", namespace);

        List<CostAnomaly> anomalies = new ArrayList<>();

        try {
            // Get recent history
            List<CostSnapshot> history = snapshotRepository
                .findByNamespaceOrderByTimestampDesc(namespace);

            if (history.size() < 2) {
                logger.info("Not enough data to detect anomalies for {}", namespace);
                return anomalies;
            }

            // Calculate baseline (average of older snapshots)
            double baseline = calculateBaseline(history);
            CostSnapshot latest = history.get(0);
            double currentCost = latest.getTotalMonthlyCost();

            // Check for cost spike or drop
            double changePercent = baseline > 0 ? ((currentCost - baseline) / baseline) * 100 : 0;

            if (Math.abs(changePercent) > SPIKE_THRESHOLD) {
                String type = changePercent > 0 ? "SPIKE" : "DROP";
                String severity = Math.abs(changePercent) > CRITICAL_THRESHOLD ? "CRITICAL" : "HIGH";

                // Identify culprit (pod with biggest change)
                String culprit = identifyCulprit(namespace, history);

                CostAnomaly anomaly = new CostAnomaly(
                    namespace, type, severity, baseline, currentCost, culprit
                );
                anomaly.setAnomalyStarted(latest.getTimestamp());
                anomaly.setMessage(generateAnomalyMessage(type, changePercent, culprit));

                anomalies.add(anomaly);

                logger.warn("⚠️ {} detected: {}% change (${} → ${}), culprit: {}",
                    type, String.format("%.1f", changePercent),
                    String.format("%.2f", baseline),
                    String.format("%.2f", currentCost),
                    culprit);
            }

            // Check for unusual patterns (high variance)
            if (history.size() >= 5) {
                CostAnomaly varianceAnomaly = detectHighVariance(namespace, history);
                if (varianceAnomaly != null) {
                    anomalies.add(varianceAnomaly);
                }
            }

        } catch (Exception e) {
            logger.error("Error detecting anomalies for {}: {}", namespace, e.getMessage());
        }

        return anomalies;
    }

    /**
     * Detect anomalies across all namespaces
     */
    public List<CostAnomaly> detectAllAnomalies() {
        logger.info("🔍 Detecting cost anomalies cluster-wide");

        List<CostAnomaly> allAnomalies = new ArrayList<>();

        try {
            // Get all unique namespaces from snapshots
            List<String> namespaces = snapshotRepository.findAll().stream()
                .map(CostSnapshot::getNamespace)
                .distinct()
                .collect(Collectors.toList());

            for (String namespace : namespaces) {
                if (namespace.startsWith("kube-")) continue; // Skip system namespaces

                List<CostAnomaly> nsAnomalies = detectAnomalies(namespace);
                allAnomalies.addAll(nsAnomalies);
            }

            logger.info("✅ Found {} anomalies across {} namespaces",
                allAnomalies.size(), namespaces.size());

        } catch (Exception e) {
            logger.error("Error detecting cluster-wide anomalies: {}", e.getMessage());
        }

        return allAnomalies;
    }

    /**
     * Calculate baseline cost (average of recent snapshots, excluding latest)
     */
    private double calculateBaseline(List<CostSnapshot> history) {
        if (history.size() < 2) return 0;

        // Use snapshots 1-7 (skip index 0 which is current)
        int startIdx = 1;
        int endIdx = Math.min(BASELINE_DAYS + 1, history.size());

        double sum = 0;
        int count = 0;

        for (int i = startIdx; i < endIdx; i++) {
            sum += history.get(i).getTotalMonthlyCost();
            count++;
        }

        return count > 0 ? sum / count : 0;
    }

    /**
     * Identify which pod is causing the cost change
     */
    private String identifyCulprit(String namespace, List<CostSnapshot> history) {
        if (history.size() < 2) return "Unknown";

        try {
            // Get current pod costs
            var currentAnalysis = costAnalysisService.analyzeNamespaceCost(namespace);
            List<ResourceCost> currentPods = currentAnalysis.getPodCosts();

            // Find pod with highest cost
            ResourceCost mostExpensive = currentPods.stream()
                .max(Comparator.comparingDouble(ResourceCost::getMonthlyCost))
                .orElse(null);

            if (mostExpensive != null) {
                // Check if this pod exists in history
                CostSnapshot oldSnapshot = history.get(Math.min(3, history.size() - 1));
                
                // If pod cost is significantly higher than average
                double avgPodCost = currentPods.stream()
                    .mapToDouble(ResourceCost::getMonthlyCost)
                    .average()
                    .orElse(0);

                if (mostExpensive.getMonthlyCost() > avgPodCost * 1.5) {
                    return mostExpensive.getDeploymentName() != null ? 
                        mostExpensive.getDeploymentName() : mostExpensive.getPodName();
                }
            }

            return "Multiple pods";

        } catch (ApiException e) {
            logger.error("Error identifying culprit: {}", e.getMessage());
            return "Unknown";
        }
    }

    /**
     * Detect high variance (unstable costs)
     */
    private CostAnomaly detectHighVariance(String namespace, List<CostSnapshot> history) {
        if (history.size() < 5) return null;

        // Calculate standard deviation
        double mean = history.stream()
            .mapToDouble(CostSnapshot::getTotalMonthlyCost)
            .average()
            .orElse(0);

        double variance = history.stream()
            .mapToDouble(s -> Math.pow(s.getTotalMonthlyCost() - mean, 2))
            .average()
            .orElse(0);

        double stdDev = Math.sqrt(variance);
        double coefficientOfVariation = mean > 0 ? (stdDev / mean) * 100 : 0;

        // If variance is > 40% of mean, flag as unusual pattern
        if (coefficientOfVariation > 40) {
            CostAnomaly anomaly = new CostAnomaly();
            anomaly.setNamespace(namespace);
            anomaly.setType("UNUSUAL_PATTERN");
            anomaly.setSeverity("MEDIUM");
            anomaly.setBaselineCost(mean);
            anomaly.setCurrentCost(history.get(0).getTotalMonthlyCost());
            anomaly.setMessage(String.format(
                "Costs are highly variable (%.0f%% variation). This indicates unstable workload patterns.",
                coefficientOfVariation
            ));
            anomaly.setCulprit("Variable workload");
            anomaly.setAnomalyStarted(history.get(history.size() - 1).getTimestamp());

            logger.info("📊 High variance detected in {}: {}% CV", namespace, 
                String.format("%.1f", coefficientOfVariation));

            return anomaly;
        }

        return null;
    }

    /**
     * Generate human-readable anomaly message
     */
    private String generateAnomalyMessage(String type, double changePercent, String culprit) {
        String direction = type.equals("SPIKE") ? "increased" : "decreased";
        String emoji = type.equals("SPIKE") ? "📈" : "📉";

        return String.format("%s Cost %s by %.1f%%. Primary contributor: %s",
            emoji, direction, Math.abs(changePercent), culprit);
    }

    /**
     * DEMO HELPER: Simulate an anomaly for testing
     */
    public CostAnomaly simulateAnomaly(String namespace, String type) {
        logger.info("🎬 Simulating {} anomaly for demo purposes", type);

        CostAnomaly anomaly = new CostAnomaly();
        anomaly.setNamespace(namespace);
        anomaly.setType(type);
        anomaly.setSeverity("HIGH");
        anomaly.setBaselineCost(100.0);
        anomaly.setDetectedAt(LocalDateTime.now());
        anomaly.setAnomalyStarted(LocalDateTime.now().minusHours(2));

        if (type.equals("SPIKE")) {
            anomaly.setCurrentCost(155.0);
            anomaly.setChangePercent(55.0);
            anomaly.setCulprit("nginx-deployment");
            anomaly.setMessage("📈 Cost increased by 55.0%. Primary contributor: nginx-deployment (scaled from 2 to 8 replicas)");
        } else {
            anomaly.setCurrentCost(65.0);
            anomaly.setChangePercent(-35.0);
            anomaly.setCulprit("backend-service");
            anomaly.setMessage("📉 Cost decreased by 35.0%. Primary contributor: backend-service (scaled down)");
        }

        return anomaly;
    }
}