package com.example.demo.controller.AutoRemediation;

import com.example.demo.model.AutoRemediation.RemediationAction;
import com.example.demo.model.AutoRemediation.RemediationPolicy;
import com.example.demo.service.AutoRemediation.RemediationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 🎛️ AUTO-REMEDIATION API
 * 
 * Endpoints:
 * - GET  /api/remediation/history       → View all remediation actions
 * - GET  /api/remediation/stats         → Get statistics
 * - GET  /api/remediation/policy        → Get current policy
 * - PUT  /api/remediation/policy        → Update policy
 * - POST /api/remediation/trigger       → Manually trigger a scan
 * - POST /api/remediation/enable        → Enable/disable auto-remediation
 */
@RestController
@RequestMapping("/api/remediation")
@CrossOrigin(origins = "http://localhost:4200")
public class RemediationController {
    private static final Logger logger = LoggerFactory.getLogger(RemediationController.class);

    @Autowired
    private RemediationService remediationService;

    /**
     * 📜 Get remediation history
     * Returns the last N remediation actions taken
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<RemediationAction> actions = remediationService.getRecentActions(limit);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", actions.size(),
                "actions", actions
            ));
        } catch (Exception e) {
            logger.error("Error fetching remediation history", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 📊 Get remediation statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = remediationService.getStatistics();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "stats", stats
            ));
        } catch (Exception e) {
            logger.error("Error fetching statistics", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 📋 Get current remediation policy
     */
    @GetMapping("/policy")
    public ResponseEntity<Map<String, Object>> getPolicy() {
        try {
            RemediationPolicy policy = remediationService.getPolicy();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "policy", policy
            ));
        } catch (Exception e) {
            logger.error("Error fetching policy", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * ✏️ Update remediation policy
     */
    @PutMapping("/policy")
    public ResponseEntity<Map<String, Object>> updatePolicy(
            @RequestBody RemediationPolicy policy) {
        try {
            remediationService.updatePolicy(policy);
            
            logger.info("Policy updated via API: enabled={}, autoRestart={}, maxAttempts={}", 
                policy.isEnabled(), 
                policy.isAutoRestartCrashingPods(), 
                policy.getMaxRestartAttempts());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Policy updated successfully",
                "policy", policy
            ));
        } catch (Exception e) {
            logger.error("Error updating policy", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 🚀 Manually trigger a remediation scan
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerScan() {
        try {
            logger.info("🎯 Manual scan triggered via API");
            remediationService.triggerManualScan();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Manual scan triggered successfully"
            ));
        } catch (Exception e) {
            logger.error("Error triggering manual scan", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 🔘 Enable/disable auto-remediation
     */
    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleRemediation(
            @RequestParam boolean enabled) {
        try {
            RemediationPolicy policy = remediationService.getPolicy();
            policy.setEnabled(enabled);
            remediationService.updatePolicy(policy);
            
            logger.info("Auto-remediation {} via API", enabled ? "ENABLED" : "DISABLED");
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "enabled", enabled,
                "message", "Auto-remediation " + (enabled ? "enabled" : "disabled")
            ));
        } catch (Exception e) {
            logger.error("Error toggling remediation", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * 🏥 Health check for remediation service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        RemediationPolicy policy = remediationService.getPolicy();
        Map<String, Object> stats = remediationService.getStatistics();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "status", policy.isEnabled() ? "active" : "disabled",
            "stats", stats,
            "message", "Auto-remediation engine operational"
        ));
    }
}