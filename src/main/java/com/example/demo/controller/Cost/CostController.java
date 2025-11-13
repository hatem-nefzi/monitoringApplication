package com.example.demo.controller.Cost;

import com.example.demo.model.Cost.*;
import com.example.demo.service.Cost.CostAnalysisService;
import com.example.demo.service.Cost.CostHistoryService;

import io.kubernetes.client.openapi.ApiException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
//for the ml cost prediction endpoints
import com.example.demo.service.Cost.CostPredictionService;
import com.example.demo.service.Cost.CostSchedulerService;
import com.example.demo.service.Cost.CostAnomalyDetector;
//For caching
import com.example.demo.service.Cost.CostCachingHelper;
//for caching 

import java.util.Map;
import java.util.stream.Collectors;




/**
 * 💰 COST MONITORING API
 * 
 * Endpoints:
 * - GET  /api/cost/analysis/{namespace}  → Cost breakdown for namespace
 * - GET  /api/cost/summary               → Cluster-wide cost summary
 * - GET  /api/cost/recommendations       → All optimization recommendations
 * - GET  /api/cost/health                → Cost monitoring status
 */
@RestController
@RequestMapping("/api/cost")
@CrossOrigin(origins = "http://localhost:4200")
public class CostController {
    private static final Logger logger = LoggerFactory.getLogger(CostController.class);

    @Autowired
    private CostAnalysisService costAnalysisService;
    @Autowired
    private CostHistoryService costHistoryService;
    // Add CostPredictionService and CostAnomalyDetector for the ml endpoints
    @Autowired
    private CostPredictionService costPredictionService;

    @Autowired
    private CostAnomalyDetector costAnomalyDetector;
    @Autowired
    private CostSchedulerService schedulerService;

    //for caching 
    @Autowired(required = false)
    private CostCachingHelper cachingHelper;
    //for caching 

    /**
     * Get cost analysis for a specific namespace
     */
    // i'm updating this endpoint to use caching 
    @GetMapping("/analysis/{namespace}")
    public ResponseEntity<Map<String, Object>> getNamespaceCostAnalysis(
            @PathVariable String namespace,@RequestParam(required = false, defaultValue = "false") boolean refreshCache) {
        try {
            long startTime = System.currentTimeMillis();
            logger.info("📊 Request: Cost analysis for namespace '{}'", namespace, refreshCache);
            

            CostAnalysis analysis = costAnalysisService.analyzeNamespaceCost(namespace, refreshCache);
            long duration = System.currentTimeMillis() - startTime;
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "namespace", namespace,
                "analysis", analysis,
                "cached", !refreshCache,
                "responseTimeMs", duration,
                "Timestamp", System.currentTimeMillis()



            ));
        } catch (ApiException e) {
            logger.error("API error fetching cost analysis for {}: {}", 
                namespace, e.getResponseBody(), e);
            return ResponseEntity.status(e.getCode())
                .body(Map.of(
                    "success", false,
                    "error", "Kubernetes API error",
                    "details", e.getResponseBody()
                ));
        } catch (Exception e) {
            logger.error("Error fetching cost analysis for {}: {}", namespace, e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }

    /**
     * Get cluster-wide cost summary
     */
    // improving this endpoint to use caching
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getClusterCostSummary(@RequestParam (required = false, defaultValue = "false") boolean refreshCache) {
        try {
            long startTime = System.currentTimeMillis();
            logger.info("📊 Request: Cluster cost summary");

            
           

            ClusterCostSummary summary = costAnalysisService.getClusterCostSummary(refreshCache);
            long duration = System.currentTimeMillis() - startTime;
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "summary", summary,
                "cached", !refreshCache,
                "responseTimeMs", duration,
                "Timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            logger.error("Error fetching cluster cost summary: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }

    /**
     * Get all cost optimization recommendations
     */
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getRecommendations(
            @RequestParam(required = false) String namespace) {
        try {
            logger.info("💡 Request: Cost recommendations{}", 
                namespace != null ? " for " + namespace : " (all namespaces)");
            
            if (namespace != null) {
                // Recommendations for specific namespace
                CostAnalysis analysis = costAnalysisService.analyzeNamespaceCost(namespace);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "namespace", namespace,
                    "recommendations", analysis.getRecommendations(),
                    "potentialSavings", analysis.getRecommendations().stream()
                        .mapToDouble(CostRecommendation::getPotentialSavings)
                        .sum()
                ));
            } else {
                // All recommendations cluster-wide
                ClusterCostSummary summary = costAnalysisService.getClusterCostSummary();
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "potentialSavings", summary.getPotentialSavings(),
                    "message", String.format("You could save $%.2f/month by optimizing resources", 
                        summary.getPotentialSavings())
                ));
            }
        } catch (Exception e) {
            logger.error("Error fetching recommendations: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
        }
    }

    /**
     * Health check for cost monitoring service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "status", "operational",
            "message", "Cost monitoring service is running",
            "cachingEnabled", cachingHelper != null,
            "timestamp", System.currentTimeMillis(),
            "features", Map.of(
                "namespaceAnalysis", true,
                "clusterSummary", true,
                "recommendations", true,
                "efficiencyScoring", true,
                "caching", cachingHelper != null,
                "forceRefresh", cachingHelper != null
            )
        ));
    }

    /**
     * Get cost configuration (rates)
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getCostConfiguration() {
        // This could be enhanced to return actual configured rates
        return ResponseEntity.ok(Map.of(
            "success", true,
            "config", Map.of(
                "cpuCostPerHour", 0.031,
                "memoryCostPerHourPerGb", 0.004,
                "storageCostPerMonthPerGb", 0.10,
                "currency", "USD",
                "note", "Rates are approximate and configurable via application.properties"
            )
        ));
    }
    // Add these endpoints to your existing CostController.java

/**
 * Get cost history for a namespace
 */
@GetMapping("/history/{namespace}")
public ResponseEntity<Map<String, Object>> getCostHistory(
        @PathVariable String namespace,
        @RequestParam(defaultValue = "30") int days) {
    try {
        logger.info("📈 Request: Cost history for namespace '{}' ({} days)", namespace, days);
        
        List<CostSnapshot> history = costHistoryService.getCostHistory(namespace, days);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "namespace", namespace,
            "days", days,
            "history", history,
            "dataPoints", history.size()
        ));
    } catch (Exception e) {
        logger.error("Error fetching cost history for {}: {}", namespace, e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 * Get savings calculation for a namespace
 */
@GetMapping("/savings/{namespace}")
public ResponseEntity<Map<String, Object>> getSavings(@PathVariable String namespace) {
    try {
        logger.info("💰 Request: Savings calculation for namespace '{}'", namespace);
        
        Map<String, Object> savings = costHistoryService.calculateSavings(namespace);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "namespace", namespace,
            "savings", savings
        ));
    } catch (Exception e) {
        logger.error("Error calculating savings for {}: {}", namespace, e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 * Get cost trend analysis
 */
@GetMapping("/trend/{namespace}")
public ResponseEntity<Map<String, Object>> getCostTrend(
        @PathVariable String namespace,
        @RequestParam(defaultValue = "30") int days) {
    try {
        logger.info("📊 Request: Cost trend for namespace '{}' ({} days)", namespace, days);
        
        Map<String, Object> trend = costHistoryService.analyzeTrend(namespace, days);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "namespace", namespace,
            "trend", trend
        ));
    } catch (Exception e) {
        logger.error("Error analyzing trend for {}: {}", namespace, e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 * Manually trigger a cost snapshot
 */
@PostMapping("/snapshot/{namespace}")
public ResponseEntity<Map<String, Object>> createSnapshot(@PathVariable String namespace) {
    try {
        logger.info("📸 Request: Manual snapshot for namespace '{}'", namespace);
        
        CostSnapshot snapshot = schedulerService.manualSnapshot(namespace);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Snapshot created successfully",
            "snapshot", snapshot
        ));
    } catch (Exception e) {
        logger.error("Error creating snapshot for {}: {}", namespace, e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 * Get cluster-wide cost history
 */
@GetMapping("/history/cluster")
public ResponseEntity<Map<String, Object>> getClusterCostHistory(
        @RequestParam(defaultValue = "30") int days) {
    try {
        logger.info("📈 Request: Cluster-wide cost history ({} days)", days);
        
        Map<String, Object> history = costHistoryService.getClusterCostHistory(days);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "days", days,
            "history", history
        ));
    } catch (Exception e) {
        logger.error("Error fetching cluster cost history: {}", e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}
     


/**
 * 📈 Get cost forecast for a namespace
 */
@GetMapping("/forecast/{namespace}")
public ResponseEntity<Map<String, Object>> getCostForecast(
        @PathVariable String namespace,
        @RequestParam(defaultValue = "30") int days) {
    try {
        logger.info("🔮 Request: Cost forecast for namespace '{}' ({} days ahead)", namespace, days);
        
        CostForecast forecast = costPredictionService.predictCost(namespace, days);
        
        // Build explicit map to avoid null serialization issues
        Map<String, Object> forecastData = new LinkedHashMap<>();
        forecastData.put("namespace", forecast.getNamespace());
        forecastData.put("currentMonthlyCost", forecast.getCurrentMonthlyCost());
        forecastData.put("predictedMonthlyCost", forecast.getPredictedMonthlyCost());
        forecastData.put("predictedWeeklyCost", forecast.getPredictedWeeklyCost());
        forecastData.put("daysAhead", forecast.getDaysAhead());
        forecastData.put("trend", forecast.getTrend());
        forecastData.put("confidence", forecast.getConfidence());
        forecastData.put("changePercent", forecast.getChangePercent());
        forecastData.put("snapshotsUsed", forecast.getSnapshotsUsed());
        forecastData.put("riskLevel", forecast.getRiskLevel());
        forecastData.put("message", forecast.getMessage());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "namespace", namespace,
            "forecast", forecastData
        ));
    } catch (Exception e) {
        logger.error("Error forecasting costs for {}: {}", namespace, e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 *  Get cost forecast for entire cluster
 */
@GetMapping("/forecast/cluster")
public ResponseEntity<Map<String, Object>> getClusterCostForecast(
        @RequestParam(defaultValue = "30") int days) {
    try {
        logger.info("🔮 Request: Cluster-wide cost forecast ({} days ahead)", days);
        
        List<String> namespaces = costAnalysisService.getAllNamespaces();
        Map<String, CostForecast> forecasts = new HashMap<>();
        double totalCurrent = 0;
        double totalPredicted = 0;
        
        for (String namespace : namespaces) {
            if (namespace.startsWith("kube-")) continue;
            
            try {
                CostForecast forecast = costPredictionService.predictCost(namespace, days);
                if (forecast.getSnapshotsUsed() > 0) {
                    forecasts.put(namespace, forecast);
                    totalCurrent += forecast.getCurrentMonthlyCost();
                    totalPredicted += forecast.getPredictedMonthlyCost();
                }
            } catch (Exception e) {
                logger.warn("Failed to forecast {}: {}", namespace, e.getMessage());
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "days", days,
            "forecasts", forecasts,
            "summary", Map.of(
                "currentTotal", totalCurrent,
                "predictedTotal", totalPredicted,
                "changePercent", totalCurrent > 0 ? 
                    ((totalPredicted - totalCurrent) / totalCurrent) * 100 : 0
            )
        ));
    } catch (Exception e) {
        logger.error("Error forecasting cluster costs: {}", e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 * Get cost anomalies for a namespace
 */
@GetMapping("/anomalies/{namespace}")
public ResponseEntity<Map<String, Object>> getNamespaceAnomalies(
        @PathVariable String namespace) {
    try {
        logger.info("🔍 Request: Cost anomalies for namespace '{}'", namespace);
        
        List<CostAnomaly> anomalies = costAnomalyDetector.detectAnomalies(namespace);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "namespace", namespace,
            "anomalies", anomalies,
            "count", anomalies.size()
        ));
    } catch (Exception e) {
        logger.error("Error detecting anomalies for {}: {}", namespace, e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 *  Get all cost anomalies cluster-wide
 */
@GetMapping("/anomalies")
public ResponseEntity<Map<String, Object>> getAllAnomalies() {
    try {
        logger.info("🔍 Request: All cost anomalies cluster-wide");
        
        List<CostAnomaly> anomalies = costAnomalyDetector.detectAllAnomalies();
        
        // Group by severity
        Map<String, Long> bySeverity = anomalies.stream()
            .collect(Collectors.groupingBy(CostAnomaly::getSeverity, Collectors.counting()));
        
        // Get critical ones
        List<CostAnomaly> critical = anomalies.stream()
            .filter(a -> "CRITICAL".equals(a.getSeverity()) || "HIGH".equals(a.getSeverity()))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "anomalies", anomalies,
            "totalCount", anomalies.size(),
            "criticalCount", critical.size(),
            "bySeverity", bySeverity,
            "critical", critical
        ));
    } catch (Exception e) {
        logger.error("Error detecting cluster anomalies: {}", e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 *  DEMO: Simulate an anomaly for testing
 */
@PostMapping("/anomalies/simulate")
public ResponseEntity<Map<String, Object>> simulateAnomaly(
        @RequestParam String namespace,
        @RequestParam(defaultValue = "SPIKE") String type) {
    try {
        logger.info("🎬 Request: Simulate {} anomaly for demo", type);
        
        CostAnomaly anomaly = costAnomalyDetector.simulateAnomaly(namespace, type);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Demo anomaly simulated successfully",
            "anomaly", anomaly
        ));
    } catch (Exception e) {
        logger.error("Error simulating anomaly: {}", e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 *  Get comprehensive cost intelligence dashboard
 */
@GetMapping("/intelligence/{namespace}")
public ResponseEntity<Map<String, Object>> getCostIntelligence(
        @PathVariable String namespace) {
    try {
        logger.info("🧠 Request: Cost intelligence dashboard for '{}'", namespace);
        
        // Get all the data
        CostAnalysis analysis = costAnalysisService.analyzeNamespaceCost(namespace);
        CostForecast forecast = costPredictionService.predictCost(namespace, 30);
        List<CostAnomaly> anomalies = costAnomalyDetector.detectAnomalies(namespace);
        Map<String, Object> savings = costHistoryService.calculateSavings(namespace);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "namespace", namespace,
            "intelligence", Map.of(
                "currentAnalysis", analysis,
                "forecast", forecast,
                "anomalies", anomalies,
                "savings", savings,
                "healthScore", calculateHealthScore(analysis, forecast, anomalies)
            )
        ));
    } catch (Exception e) {
        logger.error("Error fetching cost intelligence for {}: {}", namespace, e.getMessage(), e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}

/**
 * Calculate overall cost health score (0-100)
 */
private int calculateHealthScore(CostAnalysis analysis, CostForecast forecast, 
                                 List<CostAnomaly> anomalies) {
    int score = 100;
    
    // Deduct for efficiency
    if (analysis.getEfficiencyScore() < 50) score -= 20;
    else if (analysis.getEfficiencyScore() < 70) score -= 10;
    
    // Deduct for forecast risk
    if ("HIGH".equals(forecast.getRiskLevel())) score -= 15;
    else if ("MEDIUM".equals(forecast.getRiskLevel())) score -= 5;
    
    // Deduct for anomalies
    long criticalAnomalies = anomalies.stream()
        .filter(a -> "CRITICAL".equals(a.getSeverity()) || "HIGH".equals(a.getSeverity()))
        .count();
    score -= (int)(criticalAnomalies * 10);
    
    return Math.max(0, Math.min(100, score));
}


/**
     * Manually invalidate cache for a specific namespace
     * POST /api/cost/cache/invalidate/default
     */
    @PostMapping("/cache/invalidate/{namespace}")
    public ResponseEntity<Map<String, Object>> invalidateCache(
            @PathVariable String namespace) {
        
        if (cachingHelper == null) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Caching is not enabled"
            ));
        }
        
        try {
            cachingHelper.invalidateNamespace(namespace);
            logger.info("🧹 Cache manually invalidated for namespace: {}", namespace);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cache invalidated for namespace: " + namespace,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            logger.error("Error invalidating cache for {}: {}", namespace, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Clear ALL caches (cluster-wide)
     * POST /api/cost/cache/clear
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        
        if (cachingHelper == null) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Caching is not enabled"
            ));
        }
        
        try {
            // Invalidate all namespaces
            List<String> namespaces = costAnalysisService.getAllNamespaces();
            for (String ns : namespaces) {
                cachingHelper.invalidateNamespace(ns);
            }
            
            logger.info("🧹 All caches cleared - {} namespaces", namespaces.size());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All caches cleared",
                "namespacesCleared", namespaces.size(),
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            logger.error("Error clearing all caches: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    
}