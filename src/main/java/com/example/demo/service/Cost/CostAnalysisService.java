package com.example.demo.service.Cost;

import com.example.demo.model.Cost.*;
import com.example.demo.model.PodInfo;
import com.example.demo.service.KubernetesService;
import com.example.demo.service.PodMetricsService; // ✅ ADD THIS
import io.kubernetes.client.openapi.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CostAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(CostAnalysisService.class);

    @Autowired
    private KubernetesService kubernetesService;

    @Autowired
    private PodMetricsService podMetricsService; // ✅ ADD THIS

    @Value("${cost.cpu.per.hour:0.031}")
    private double cpuCostPerHour;

    @Value("${cost.memory.gb.per.hour:0.004}")
    private double memoryCostPerHour;

    @Value("${cost.storage.gb.per.month:0.10}")
    private double storageCostPerMonth;

    /**
 * Analyze costs for a specific namespace
 */
public CostAnalysis analyzeNamespaceCost(String namespace) throws ApiException {
    logger.info("💰 Analyzing costs for namespace: {}", namespace);

    List<PodInfo> pods = kubernetesService.getPodInfoClusterWide().stream()
        .filter(pod -> pod.getNamespace().equals(namespace))
        .collect(Collectors.toList());

    CostAnalysis analysis = new CostAnalysis();
    analysis.setNamespace(namespace);
    analysis.setTotalPods(pods.size());

    double totalHourlyCost = 0;
    double totalCpu = 0;
    double totalMemory = 0;
    List<ResourceCost> resourceCosts = new ArrayList<>();

    for (PodInfo pod : pods) {
        // ✅ FETCH ACTUAL USAGE METRICS (from metrics server)
        Map<String, String> actualUsage = podMetricsService.getPodMetrics(namespace, pod.getName());
        
        // ✅ FETCH RESOURCE REQUESTS (from pod spec)
        Map<String, String> resourceRequests = kubernetesService.getPodResourceRequests(namespace, pod.getName());
        
        ResourceCost podCost = calculatePodCost(pod, actualUsage, resourceRequests);
        resourceCosts.add(podCost);
        
        totalHourlyCost += podCost.getHourlyCost();
        totalCpu += podCost.getCpuRequest();
        totalMemory += podCost.getMemoryRequest();
    }

    analysis.setHourlyCost(totalHourlyCost);
    analysis.setMonthlyCost(totalHourlyCost * 24 * 30);
    analysis.setTotalCpuCores(totalCpu);
    analysis.setTotalMemoryGb(totalMemory);
    analysis.setPodCosts(resourceCosts);

    // Calculate efficiency score
    double efficiencyScore = calculateEfficiencyScore(resourceCosts);
    analysis.setEfficiencyScore(efficiencyScore);

    // Generate recommendations
    List<CostRecommendation> recommendations = generateRecommendations(resourceCosts);
    analysis.setRecommendations(recommendations);

    // ✅ Calculate total potential savings
    double totalSavings = recommendations.stream()
        .mapToDouble(CostRecommendation::getPotentialSavings)
        .sum();
    
    logger.info("✅ Cost analysis complete: ${}/month for {} pods ({}% efficient) - Potential savings: ${}/month", 
        String.format("%.2f", analysis.getMonthlyCost()), 
        pods.size(), 
        String.format("%.0f", efficiencyScore),
        String.format("%.2f", totalSavings));

    return analysis;
}
    /**
 * Calculate cost for a single pod
 */
private ResourceCost calculatePodCost(PodInfo pod, Map<String, String> actualUsage, Map<String, String> resourceRequests) {
    ResourceCost cost = new ResourceCost();
    cost.setPodName(pod.getName());
    cost.setDeploymentName(extractDeploymentName(pod.getName()));

    // ✅ GET RESOURCE REQUESTS FROM POD SPEC (NOT FROM pod.getMetrics()!)
    double cpuRequest = parseResourceValue(resourceRequests.getOrDefault("cpuRequest", "100m"));
    double memoryRequest = parseMemoryToGb(resourceRequests.getOrDefault("memoryRequest", "128Mi"));
    
    // ✅ GET ACTUAL USAGE from metrics server
    double cpuUsage = 0;
    double memoryUsage = 0;
    
    if (actualUsage != null && !actualUsage.containsKey("error")) {
        cpuUsage = parseResourceValue(actualUsage.getOrDefault("cpu", "0m"));
        memoryUsage = parseMemoryToGb(actualUsage.getOrDefault("memory", "0Mi"));
    } else {
        logger.warn("⚠️ No metrics available for pod {}, assuming 0 usage", pod.getName());
    }

    cost.setCpuRequest(cpuRequest);
    cost.setCpuUsage(cpuUsage);
    cost.setMemoryRequest(memoryRequest);
    cost.setMemoryUsage(memoryUsage);

    // Calculate costs
    double cpuCost = cpuRequest * cpuCostPerHour;
    double memoryCost = memoryRequest * memoryCostPerHour;
    double hourly = cpuCost + memoryCost;

    cost.setHourlyCost(hourly);
    cost.setMonthlyCost(hourly * 24 * 30);

    // Calculate waste
    double cpuWaste = Math.max(0, cpuRequest - cpuUsage);
    double memoryWaste = Math.max(0, memoryRequest - memoryUsage);
    double wastedCost = (cpuWaste * cpuCostPerHour + memoryWaste * memoryCostPerHour) * 24 * 30;
    cost.setWastedCost(wastedCost);

    // Determine status
    boolean cpuOverProvisioned = cpuRequest > 0 && cpuUsage < cpuRequest * 0.5;
    boolean memoryOverProvisioned = memoryRequest > 0 && memoryUsage < memoryRequest * 0.6;
    boolean cpuUnderProvisioned = cpuRequest > 0 && cpuUsage > cpuRequest * 0.8;
    boolean memoryUnderProvisioned = memoryRequest > 0 && memoryUsage > memoryRequest * 0.8;

    if (cpuUnderProvisioned || memoryUnderProvisioned) {
        cost.setStatus("under-provisioned");
    } else if (cpuOverProvisioned || memoryOverProvisioned) {
        cost.setStatus("over-provisioned");
    } else {
        cost.setStatus("efficient");
    }

    return cost;
}

    /**
     * ✅ EXTRACT DEPLOYMENT NAME FROM POD NAME
     * Pattern: deployment-name-replicaset-hash-pod-hash
     */
    private String extractDeploymentName(String podName) {
        if (podName == null) return "unknown";
        
        // Remove pod hash (last segment after -)
        int lastDash = podName.lastIndexOf('-');
        if (lastDash <= 0) return podName;
        
        String withoutPodHash = podName.substring(0, lastDash);
        
        // Remove replicaset hash (second-to-last segment)
        int secondLastDash = withoutPodHash.lastIndexOf('-');
        if (secondLastDash <= 0) return withoutPodHash;
        
        return withoutPodHash.substring(0, secondLastDash);
    }

    /**
     * Calculate overall efficiency score (0-100)
     */
    private double calculateEfficiencyScore(List<ResourceCost> costs) {
        if (costs.isEmpty()) return 100.0;

        double totalScore = 0;
        int count = 0;

        for (ResourceCost cost : costs) {
            // ✅ HANDLE ZERO REQUESTS GRACEFULLY
            double cpuEfficiency = 100.0; // Default to 100% if no request
            double memoryEfficiency = 100.0;
            
            if (cost.getCpuRequest() > 0) {
                cpuEfficiency = Math.min(100, (cost.getCpuUsage() / cost.getCpuRequest()) * 100);
            }
            
            if (cost.getMemoryRequest() > 0) {
                memoryEfficiency = Math.min(100, (cost.getMemoryUsage() / cost.getMemoryRequest()) * 100);
            }

            double podEfficiency = (cpuEfficiency + memoryEfficiency) / 2;
            totalScore += podEfficiency;
            count++;
        }

        return count > 0 ? totalScore / count : 0;
    }

   /**
 * Generate cost optimization recommendations
 */
/**
 * Generate cost optimization recommendations
 */
private List<CostRecommendation> generateRecommendations(List<ResourceCost> costs) {
    List<CostRecommendation> recommendations = new ArrayList<>();

    for (ResourceCost cost : costs) {
        // Skip if no actual usage data
        if (cost.getCpuUsage() == 0 && cost.getMemoryUsage() == 0) {
            logger.debug("Skipping recommendations for {} - no usage data", cost.getPodName());
            continue;
        }

        // ===== CPU OVER-PROVISIONING =====
        if (cost.getCpuRequest() > 0.01 && cost.getCpuUsage() < cost.getCpuRequest() * 0.5) {
            // Recommend max(10m, usage * 2.0) for better buffer
            double recommended = Math.max(0.01, cost.getCpuUsage() * 2.0);
            double savings = (cost.getCpuRequest() - recommended) * cpuCostPerHour * 24 * 30;

            // Only add if savings > $0.10/month
            if (savings > 0.10) {
                recommendations.add(new CostRecommendation(
                    cost.getPodName(),
                    "reduce_cpu",
                    String.format("CPU: %.3f cores (%.0fm)", cost.getCpuRequest(), cost.getCpuRequest() * 1000),
                    String.format("CPU: %.3f cores (%.0fm)", recommended, recommended * 1000),
                    savings,
                    String.format("CPU usage is only %.1f%% of requested (using %.2fm out of %.0fm)", 
                        (cost.getCpuUsage() / cost.getCpuRequest()) * 100,
                        cost.getCpuUsage() * 1000,
                        cost.getCpuRequest() * 1000)
                ));
            }
        }

        // ===== MEMORY OVER-PROVISIONING =====
        if (cost.getMemoryRequest() > 0.05 && cost.getMemoryUsage() < cost.getMemoryRequest() * 0.65) {
            // Recommend max(64Mi, usage * 1.4) for safety buffer
            double recommended = Math.max(0.0625, cost.getMemoryUsage() * 1.4); // 0.0625 GB = 64Mi
            double savings = (cost.getMemoryRequest() - recommended) * memoryCostPerHour * 24 * 30;

            // Only add if savings > $0.05/month
            if (savings > 0.05) {
                recommendations.add(new CostRecommendation(
                    cost.getPodName(),
                    "reduce_memory",
                    String.format("Memory: %.2f GB (%.0fMi)", cost.getMemoryRequest(), cost.getMemoryRequest() * 1024),
                    String.format("Memory: %.2f GB (%.0fMi)", recommended, recommended * 1024),
                    savings,
                    String.format("Memory usage is only %.1f%% of requested (using %.0fMi out of %.0fMi)", 
                        (cost.getMemoryUsage() / cost.getMemoryRequest()) * 100,
                        cost.getMemoryUsage() * 1024,
                        cost.getMemoryRequest() * 1024)
                ));
            }
        }

        // ===== CPU UNDER-PROVISIONING =====
        if (cost.getCpuUsage() > cost.getCpuRequest() * 0.85 && cost.getCpuRequest() > 0) {
            double usagePercent = (cost.getCpuUsage() / cost.getCpuRequest()) * 100;
            double recommended = cost.getCpuUsage() * 1.3; // 30% buffer above current usage
            
            // ✅ Determine priority based on severity
            String priority;
            if (cost.getCpuUsage() > cost.getCpuRequest()) {
                priority = "critical";  // Already exceeding limit!
            } else if (usagePercent > 95) {
                priority = "high";
            } else {
                priority = "medium";
            }
            
            CostRecommendation rec = new CostRecommendation(
                cost.getPodName(),
                "increase_cpu",
                String.format("CPU: %.3f cores (%.0fm)", cost.getCpuRequest(), cost.getCpuRequest() * 1000),
                String.format("CPU: %.3f cores (%.0fm)", recommended, recommended * 1000),
                0,  // Not a savings
                String.format("⚠️ CPU usage is %.1f%% of limit - pod may be throttled",
                    usagePercent)
            );
            rec.setPriority(priority);  // ✅ Override constructor's priority
            recommendations.add(rec);
        }
        
        // ===== MEMORY UNDER-PROVISIONING =====
        if (cost.getMemoryUsage() > cost.getMemoryRequest() * 0.85 && cost.getMemoryRequest() > 0) {
            double usagePercent = (cost.getMemoryUsage() / cost.getMemoryRequest()) * 100;
            double recommended = cost.getMemoryUsage() * 1.3; // 30% buffer above current usage
            
            // ✅ Determine priority based on severity
            String priority;
            if (cost.getMemoryUsage() > cost.getMemoryRequest()) {
                priority = "critical";  // Already exceeding limit! Imminent OOMKill risk!
            } else if (usagePercent > 95) {
                priority = "high";
            } else {
                priority = "medium";
            }
            
            CostRecommendation rec = new CostRecommendation(
                cost.getPodName(),
                "increase_memory",
                String.format("Memory: %.2f GB (%.0fMi)", cost.getMemoryRequest(), cost.getMemoryRequest() * 1024),
                String.format("Memory: %.2f GB (%.0fMi)", recommended, recommended * 1024),
                0,  // Not a savings
                String.format("⚠️ Memory usage is %.1f%% of limit - pod may be OOMKilled",
                    usagePercent)
            );
            rec.setPriority(priority);  // ✅ Override constructor's priority
            recommendations.add(rec);
        }
    }

    // ✅ Sort by priority first, then by savings
    recommendations.sort((a, b) -> {
        // Priority order: critical > high > medium > low
        Map<String, Integer> priorityOrder = Map.of(
            "critical", 4,
            "high", 3,
            "medium", 2,
            "low", 1
        );
        
        int priorityCompare = priorityOrder.getOrDefault(b.getPriority(), 1) 
                            - priorityOrder.getOrDefault(a.getPriority(), 1);
        
        if (priorityCompare != 0) {
            return priorityCompare;  // Sort by priority first
        }
        
        // Then by savings
        return Double.compare(b.getPotentialSavings(), a.getPotentialSavings());
    });

    return recommendations;
}

    /**
     * Get cluster-wide cost summary
     */
    /**
 * Get cluster-wide cost summary
 */
public ClusterCostSummary getClusterCostSummary() throws ApiException {
    logger.info("💰 Calculating cluster-wide cost summary");

    List<String> namespaces = kubernetesService.getNamespaces();
    Map<String, Double> costByNamespace = new HashMap<>();
    
    double totalCost = 0;
    double totalWaste = 0;
    int totalPods = 0;
    int efficientPods = 0;
    int overProvisionedPods = 0;
    int underProvisionedPods = 0;  // ✅ Track under-provisioned too
    double totalEfficiency = 0;
    int namespaceCount = 0;

    String mostExpensive = "";
    double highestCost = 0;
    
    // ✅ Track least efficient namespace
    String leastEfficient = "";
    double lowestEfficiency = 100.0;

    for (String namespace : namespaces) {
        // Skip system namespaces for cleaner demo
        if (namespace.startsWith("kube-")) continue;

        try {
            CostAnalysis analysis = analyzeNamespaceCost(namespace);
            double namespaceCost = analysis.getMonthlyCost();
            double namespaceEfficiency = analysis.getEfficiencyScore();
            
            costByNamespace.put(namespace, namespaceCost);
            totalCost += namespaceCost;
            totalPods += analysis.getTotalPods();
            totalEfficiency += namespaceEfficiency;
            namespaceCount++;

            // Track most expensive
            if (namespaceCost > highestCost) {
                highestCost = namespaceCost;
                mostExpensive = namespace;
            }
            
            // ✅ Track least efficient
            if (namespaceEfficiency < lowestEfficiency && analysis.getTotalPods() > 0) {
                lowestEfficiency = namespaceEfficiency;
                leastEfficient = namespace;
            }

            // Count pod statuses
            for (ResourceCost rc : analysis.getPodCosts()) {
                totalWaste += rc.getWastedCost();
                
                if ("efficient".equals(rc.getStatus())) {
                    efficientPods++;
                } else if ("over-provisioned".equals(rc.getStatus())) {
                    overProvisionedPods++;
                } else if ("under-provisioned".equals(rc.getStatus())) {
                    underProvisionedPods++;
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to analyze namespace {}: {}", namespace, e.getMessage());
        }
    }

    ClusterCostSummary summary = new ClusterCostSummary();
    summary.setTotalMonthlyCost(totalCost);
    summary.setTotalWastedCost(totalWaste);
    summary.setPotentialSavings(totalWaste);
    summary.setTotalPods(totalPods);
    summary.setEfficientPods(efficientPods);
    summary.setOverProvisionedPods(overProvisionedPods);
    summary.setAverageEfficiencyScore(namespaceCount > 0 ? totalEfficiency / namespaceCount : 0);
    summary.setCostByNamespace(costByNamespace);
    summary.setMostExpensiveNamespace(mostExpensive);
    summary.setLeastEfficientNamespace(leastEfficient);  // ✅ Now populated!

    logger.info("✅ Cluster summary: ${}/month total, ${}/month wasted ({}% efficient)", 
        String.format("%.2f", totalCost),
        String.format("%.2f", totalWaste),
        String.format("%.0f", summary.getAverageEfficiencyScore()));
    
    // ✅ Log critical insights
    if (underProvisionedPods > 0) {
        logger.warn("⚠️ {} pods are under-provisioned - immediate action required!", underProvisionedPods);
    }
    if (totalWaste / totalCost > 0.7) {
        logger.warn("⚠️ Over 70% of resources are wasted - significant optimization opportunity!");
    }

    return summary;
}

    // ==================== UTILITY METHODS ====================

    /**
     * Parse resource value (handles m, Mi, Gi suffixes)
     */
    private double parseResourceValue(String value) {
        if (value == null || value.isEmpty()) return 0;

        try {
            if (value.endsWith("m")) {
                // Millicores to cores
                return Double.parseDouble(value.replace("m", "")) / 1000.0;
            } else if (value.endsWith("n")) {
                // Nanocores to cores (some metrics return nanocores)
                return Double.parseDouble(value.replace("n", "")) / 1_000_000_000.0;
            } else {
                return Double.parseDouble(value);
            }
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse resource value: {}", value);
            return 0;
        }
    }

    /**
     * Parse memory to GB
     */
    private double parseMemoryToGb(String value) {
        if (value == null || value.isEmpty()) return 0;

        try {
            if (value.endsWith("Mi")) {
                // Mebibytes to GB
                return Double.parseDouble(value.replace("Mi", "")) / 1024.0;
            } else if (value.endsWith("Gi")) {
                return Double.parseDouble(value.replace("Gi", ""));
            } else if (value.endsWith("Ki")) {
                return Double.parseDouble(value.replace("Ki", "")) / (1024.0 * 1024.0);
            } else if (value.endsWith("M")) {
                // Megabytes to GB
                return Double.parseDouble(value.replace("M", "")) / 1000.0;
            } else if (value.endsWith("k") || value.endsWith("K")) {
                // Kilobytes to GB
                return Double.parseDouble(value.replace("k", "").replace("K", "")) / (1024.0 * 1024.0);
            } else {
                // Assume bytes
                return Double.parseDouble(value) / (1024.0 * 1024.0 * 1024.0);
            }
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse memory value: {}", value);
            return 0;
        }
    }
    /**
 * Get all namespaces (for scheduled snapshot)
 */
public List<String> getAllNamespaces() throws ApiException {
    return kubernetesService.getNamespaces();
}
}