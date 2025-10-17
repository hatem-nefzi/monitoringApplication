package com.example.demo.controller.Cost;

import com.example.demo.model.Cost.*;
import com.example.demo.service.Cost.CostAnalysisService;
import com.example.demo.service.Cost.CostHistoryService;

import io.kubernetes.client.openapi.ApiException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    /**
     * Get cost analysis for a specific namespace
     */
    @GetMapping("/analysis/{namespace}")
    public ResponseEntity<Map<String, Object>> getNamespaceCostAnalysis(
            @PathVariable String namespace) {
        try {
            logger.info("📊 Request: Cost analysis for namespace '{}'", namespace);
            
            CostAnalysis analysis = costAnalysisService.analyzeNamespaceCost(namespace);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "namespace", namespace,
                "analysis", analysis
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
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getClusterCostSummary() {
        try {
            logger.info("📊 Request: Cluster cost summary");
            
            ClusterCostSummary summary = costAnalysisService.getClusterCostSummary();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "summary", summary
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
            "features", Map.of(
                "namespaceAnalysis", true,
                "clusterSummary", true,
                "recommendations", true,
                "efficiencyScoring", true
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
        
        CostSnapshot snapshot = costHistoryService.manualSnapshot(namespace);
        
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
}