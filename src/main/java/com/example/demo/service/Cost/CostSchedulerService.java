package com.example.demo.service.Cost;

import com.example.demo.model.Cost.CostAnalysis;
import com.example.demo.model.Cost.CostSnapshot;
import com.example.demo.service.KubernetesService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 *  COST SNAPSHOT SCHEDULER
 * 
 * Handles automatic snapshot creation on a schedule with distributed locking.
 * Uses ShedLock to ensure only ONE pod executes the scheduled task at a time.
 */
@Service
public class CostSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(CostSchedulerService.class);

    @Autowired
    private CostAnalysisService costAnalysisService;

    @Autowired
    private CostSnapshotService snapshotService;

    @Autowired
    private KubernetesService kubernetesService;

    /**
     * Scheduled task to automatically snapshot all namespaces every 15 minutes.
     * 
     * @SchedulerLock ensures only ONE pod acquires the lock and executes this.
     * - lockAtMostFor: Maximum lock duration (14 min) - prevents stuck locks
     * - lockAtLeastFor: Minimum lock duration (5 min) - prevents rapid re-execution
     */
    @Scheduled(cron = "0 */15 * * * *") // Run every 15 minutes
    @SchedulerLock(
        name = "autoSnapshotAllNamespaces",
        lockAtMostFor = "PT14M",   // Lock expires after 14 minutes
        lockAtLeastFor = "PT5M"    // Lock held for at least 5 minutes
    )
    @Transactional
    public void autoSnapshotAllNamespaces() {
        logger.info("🔒 Lock acquired - Starting automatic cost snapshot for all namespaces");
        
        try {
            List<String> namespaces = kubernetesService.getNamespaces();
            int successCount = 0;
            int errorCount = 0;

            for (String namespace : namespaces) {
                // Skip system namespaces
                if (namespace.startsWith("kube-")) {
                    continue;
                }

                try {
                    // Analyze current costs
                    CostAnalysis analysis = costAnalysisService.analyzeNamespaceCost(namespace);
                    
                    // Create snapshot
                    snapshotService.createSnapshot(namespace, analysis);
                    successCount++;
                    logger.info("✅ Auto-snapshot saved for {}", namespace);
                    
                } catch (Exception e) {
                    errorCount++;
                    logger.error("❌ Failed to snapshot {}: {}", namespace, e.getMessage());
                }
            }

            logger.info("✅ Automatic snapshot complete: {} succeeded, {} failed", 
                successCount, errorCount);
                
        } catch (Exception e) {
            logger.error("❌ Automatic snapshot failed: {}", e.getMessage(), e);
        }
        
        logger.info("🔓 Lock released - Snapshot task completed");
    }

    /**
     * Manually trigger snapshot (for testing or immediate needs)
     * No lock needed - this is triggered by API calls, not scheduled
     */
    @Transactional
    public CostSnapshot manualSnapshot(String namespace) {
        try {
            logger.info("📸 Manual snapshot triggered for {}", namespace);
            
            // Analyze current costs
            CostAnalysis analysis = costAnalysisService.analyzeNamespaceCost(namespace);
            
            // Create snapshot
            return snapshotService.createSnapshot(namespace, analysis);
            
        } catch (Exception e) {
            logger.error("❌ Manual snapshot failed for {}: {}", namespace, e.getMessage(), e);
            throw new RuntimeException("Failed to create snapshot: " + e.getMessage());
        }
    }
}