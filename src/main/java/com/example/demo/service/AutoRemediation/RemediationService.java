package com.example.demo.service.AutoRemediation;

import com.example.demo.model.AutoRemediation.RemediationAction;
import com.example.demo.model.AutoRemediation.RemediationPolicy;
import com.example.demo.model.PodInfo;
import com.example.demo.model.ContainerInfo;
import io.kubernetes.client.openapi.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.example.demo.service.KubernetesService;
import com.example.demo.service.CacheService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 🧠 AUTO-REMEDIATION ENGINE
 * 
 * This service continuously monitors your cluster and automatically fixes common issues:
 * - Restarts crashing pods
 * - Cleans up failed pods
 * - Detects resource issues
 * - Logs all actions for audit
 */
@Service
public class RemediationService {
    private static final Logger logger = LoggerFactory.getLogger(RemediationService.class);

    @Autowired
    private KubernetesService kubernetesService;
    
    @Autowired
    private CacheService cacheService;

    // In-memory tracking of remediation attempts (prevent infinite loops)
    private final Map<String, Integer> restartAttempts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastActionTime = new ConcurrentHashMap<>();
    
    // History of all actions (stored in Redis via CacheService)
    private final List<RemediationAction> recentActions = Collections.synchronizedList(new ArrayList<>());

    // Configuration
    private RemediationPolicy policy = new RemediationPolicy();

    /**
     * 🔄 MAIN MONITORING LOOP
     * Runs every 30 seconds to check for issues
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void monitorAndRemediate() {
        if (!policy.isEnabled()) {
            logger.debug("Auto-remediation is disabled");
            return;
        }

        logger.info("🔍 Starting auto-remediation scan...");
        
        try {
            List<PodInfo> allPods = kubernetesService.getPodInfoClusterWide();
            
            // DEBUG: Detailed logging for troubleshooting
            logger.info("📊 Scanning {} pods for issues", allPods.size());
            
            // Log details about pods with potential issues
            for (PodInfo pod : allPods) {
                int restarts = getTotalRestartCount(pod);
                boolean allReady = areAllContainersReady(pod);
                boolean hasWaiting = hasWaitingContainers(pod);
                
                if (restarts >= 2) { // Log any pod with 2+ restarts for debugging
                    logger.info("📝 Pod {}/{}: restarts={}, allReady={}, hasWaiting={}, containers={}", 
                        pod.getNamespace(), pod.getName(), restarts, allReady, hasWaiting,
                        pod.getContainers().stream()
                            .map(c -> c.getName() + "(" + c.getState() + ",ready:" + c.isReady() + ",r:" + c.getRestartCount() + ")")
                            .collect(Collectors.joining(", ")));
                }
            }

            int issuesFound = 0;
            int actionsTaken = 0;

            for (PodInfo pod : allPods) {
                String podKey = getPodKey(pod);

                // Skip system namespaces
                if (isSystemNamespace(pod.getNamespace())) {
                    continue;
                }

                // 1. Check for CrashLoopBackOff (FIXED LOGIC)
                if (isCrashLoopBackOff(pod)) {
                    logger.info("🎯 CRASH LOOP DETECTED: {}/{} with {} restarts", 
                        pod.getNamespace(), pod.getName(), getTotalRestartCount(pod));
                    issuesFound++;
                    if (shouldRemediate(podKey, "CrashLoopBackOff")) {
                        logger.info("🔄 TAKING ACTION: Deleting pod {}/{}", 
                            pod.getNamespace(), pod.getName());
                        handleCrashLoopBackOff(pod);
                        actionsTaken++;
                    }
                }

                // 2. Check for high restart count (alert only)
                else if (hasHighRestartCount(pod, 5)) {
                    logger.info("⚠️ HIGH RESTART COUNT: {}/{} has {} restarts", 
                        pod.getNamespace(), pod.getName(), getTotalRestartCount(pod));
                    issuesFound++;
                    handleHighRestartCount(pod);
                }

                // 3. Check for Failed pods (old)
                else if ("Failed".equals(pod.getStatus())) {
                    logger.info("⚠️ FAILED POD: {}/{}", pod.getNamespace(), pod.getName());
                    issuesFound++;
                    if (shouldRemediate(podKey, "Failed")) {
                        handleFailedPod(pod);
                        actionsTaken++;
                    }
                }

                // 4. Check for Pending too long
                else if ("Pending".equals(pod.getStatus())) {
                    logger.info("⚠️ PENDING POD: {}/{}", pod.getNamespace(), pod.getName());
                    issuesFound++;
                    handlePendingPod(pod);
                }
            }

            logger.info("✅ Scan complete: {} issues found, {} actions taken", issuesFound, actionsTaken);

        } catch (Exception e) {
            logger.error("❌ Error during monitoring: {}", e.getMessage(), e);
        }
    }

    // ==================== REMEDIATION HANDLERS ====================

    /**
     * 🔄 Handle CrashLoopBackOff: Delete pod to force recreation
     */
    private void handleCrashLoopBackOff(PodInfo pod) {
        String podKey = getPodKey(pod);
        int attempts = restartAttempts.getOrDefault(podKey, 0);

        if (attempts >= policy.getMaxRestartAttempts()) {
            logger.warn("⚠️ Pod {} exceeded max restart attempts ({}). Manual intervention needed.", 
                podKey, policy.getMaxRestartAttempts());
            
            RemediationAction action = createAction(pod, "CrashLoopBackOff", "SKIPPED", 
                "Exceeded max restart attempts");
            action.setStatus(RemediationAction.RemediationStatus.SKIPPED);
            recordAction(action);
            return;
        }

        RemediationAction action = createAction(pod, "CrashLoopBackOff", "Delete Pod (Force Restart)", 
            "Pod is in crash loop with " + getMaxRestartCount(pod) + " restarts");

        try {
            // Use your existing KubernetesService to delete the pod
            kubernetesService.deletePod(pod.getNamespace(), pod.getName());

            restartAttempts.put(podKey, attempts + 1);
            lastActionTime.put(podKey, LocalDateTime.now());

            action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
            action.setMetadata(Map.of(
                "restartAttempt", String.valueOf(attempts + 1),
                "maxAttempts", String.valueOf(policy.getMaxRestartAttempts()),
                "containerRestarts", String.valueOf(getMaxRestartCount(pod)),
                "totalRestarts", String.valueOf(getTotalRestartCount(pod))
            ));

            logger.info("✅ Deleted pod {} to fix crash loop (attempt {}/{})", 
                podKey, attempts + 1, policy.getMaxRestartAttempts());

        } catch (ApiException e) {
            action.setStatus(RemediationAction.RemediationStatus.FAILED);
            action.setError(e.getResponseBody());
            logger.error("❌ Failed to delete pod {}: {}", podKey, e.getResponseBody());
        }

        recordAction(action);
    }

    /**
     * 🗑️ Handle Failed pods: Clean up old failed pods
     */
    private void handleFailedPod(PodInfo pod) {
        if (!policy.isAutoDeleteFailedPods()) {
            logger.info("⏸️ Auto-delete for failed pods is disabled");
            return; // Feature disabled
        }

        RemediationAction action = createAction(pod, "Failed", "Delete Failed Pod", 
            "Cleaning up failed pod");

        try {
            kubernetesService.deletePod(pod.getNamespace(), pod.getName());

            action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
            logger.info("✅ Deleted failed pod {}", getPodKey(pod));

        } catch (ApiException e) {
            action.setStatus(RemediationAction.RemediationStatus.FAILED);
            action.setError(e.getResponseBody());
            logger.error("❌ Failed to delete failed pod {}: {}", getPodKey(pod), e.getResponseBody());
        }

        recordAction(action);
    }

    /**
     * 🔁 Handle high restart count: Just alert
     */
    private void handleHighRestartCount(PodInfo pod) {
        int restartCount = getTotalRestartCount(pod);
        
        RemediationAction action = createAction(pod, "HighRestartCount", "MONITOR", 
            "Pod has high restart count - investigate root cause");
        action.setStatus(RemediationAction.RemediationStatus.SKIPPED);
        action.setMetadata(Map.of("restartCount", String.valueOf(restartCount)));
        
        logger.warn("⚠️ Pod {} has high restart count: {}", getPodKey(pod), restartCount);
        recordAction(action);
    }

    /**
     * ⏳ Handle pending pod: Alert about resource issues
     */
    private void handlePendingPod(PodInfo pod) {
        RemediationAction action = createAction(pod, "PendingTooLong", "INVESTIGATE", 
            "Pod pending for too long - check node resources");
        action.setStatus(RemediationAction.RemediationStatus.SKIPPED);
        
        logger.warn("⚠️ Pod {} is pending - check cluster resources", getPodKey(pod));
        recordAction(action);
    }

    // ==================== DETECTION HELPERS (FIXED) ====================

    /**
     * FIXED: A pod is in crash loop if it has high restart count AND is not running properly
     */
    private boolean isCrashLoopBackOff(PodInfo pod) {
        if (pod.getContainers() == null || pod.getContainers().isEmpty()) {
            return false;
        }
        
        boolean hasHighRestarts = getTotalRestartCount(pod) >= 3;
        boolean allContainersReady = areAllContainersReady(pod);
        boolean hasWaitingContainers = hasWaitingContainers(pod);
        
        // Pod is in crash loop if it has high restarts AND (not all ready OR has waiting containers)
        boolean isCrashLoop = hasHighRestarts && (!allContainersReady || hasWaitingContainers);
        
        if (isCrashLoop) {
            logger.debug("🚨 Crash loop detected for {}/{}: restarts={}, allReady={}, hasWaiting={}",
                pod.getNamespace(), pod.getName(), getTotalRestartCount(pod), allContainersReady, hasWaitingContainers);
        }
        
        return isCrashLoop;
    }

    private boolean areAllContainersReady(PodInfo pod) {
        if (pod.getContainers() == null) return false;
        return pod.getContainers().stream().allMatch(ContainerInfo::isReady);
    }

    private boolean hasWaitingContainers(PodInfo pod) {
        if (pod.getContainers() == null) return false;
        return pod.getContainers().stream()
            .anyMatch(container -> "Waiting".equals(container.getState()));
    }

    private boolean hasHighRestartCount(PodInfo pod, int threshold) {
        return getTotalRestartCount(pod) >= threshold;
    }

    private int getTotalRestartCount(PodInfo pod) {
        if (pod.getContainers() == null) return 0;
        return pod.getContainers().stream()
            .mapToInt(ContainerInfo::getRestartCount)
            .sum();
    }

    private int getMaxRestartCount(PodInfo pod) {
        if (pod.getContainers() == null) return 0;
        return pod.getContainers().stream()
            .mapToInt(ContainerInfo::getRestartCount)
            .max()
            .orElse(0);
    }

    private boolean isOldEnough(PodInfo pod, int minutes) {
        // For now, we'll act on any pod in bad state regardless of age
        return true;
    }

    // ==================== UTILITY METHODS ====================

    private boolean shouldRemediate(String podKey, String issue) {
        // Check if we've acted on this pod recently (backoff)
        LocalDateTime lastAction = lastActionTime.get(podKey);
        if (lastAction != null && ChronoUnit.MINUTES.between(lastAction, LocalDateTime.now()) < 2) {
            logger.debug("Skipping {} - acted recently", podKey);
            return false;
        }
        return true;
    }

    private boolean isSystemNamespace(String namespace) {
        return namespace.startsWith("kube-") || 
               "cattle-system".equals(namespace) ||
               "ingress-nginx".equals(namespace);
        // NOTE: "default" namespace is NOT excluded anymore so we can test there
    }

    private String getPodKey(PodInfo pod) {
        return pod.getNamespace() + "/" + pod.getName();
    }

    private RemediationAction createAction(PodInfo pod, String issue, String action, String reason) {
        RemediationAction remediationAction = new RemediationAction(
            pod.getName(),
            pod.getNamespace(),
            issue,
            action,
            reason
        );
        remediationAction.setTimestamp(LocalDateTime.now());
        return remediationAction;
    }

    private void recordAction(RemediationAction action) {
        recentActions.add(action);
        
        // Keep only last 100 actions in memory
        if (recentActions.size() > 100) {
            recentActions.remove(0);
        }

        // Store in Redis for persistence
        try {
            cacheService.storeRemediationAction(action);
        } catch (Exception e) {
            logger.warn("Failed to store action in Redis: {}", e.getMessage());
        }

        logger.info("📝 Recorded action: {}", action);
    }

    // ==================== PUBLIC API ====================

    public List<RemediationAction> getRecentActions(int limit) {
        int size = recentActions.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(recentActions.subList(fromIndex, size));
    }

    public RemediationPolicy getPolicy() {
        return policy;
    }

    public void updatePolicy(RemediationPolicy newPolicy) {
        this.policy = newPolicy;
        logger.info("📋 Policy updated: {}", newPolicy);
    }

    public Map<String, Object> getStatistics() {
        long successCount = recentActions.stream()
            .filter(a -> a.getStatus() == RemediationAction.RemediationStatus.SUCCESS)
            .count();
        
        long failedCount = recentActions.stream()
            .filter(a -> a.getStatus() == RemediationAction.RemediationStatus.FAILED)
            .count();

        return Map.of(
            "totalActions", recentActions.size(),
            "successfulActions", successCount,
            "failedActions", failedCount,
            "policyEnabled", policy.isEnabled(),
            "trackedPods", restartAttempts.size()
        );
    }

    /**
     * Manual trigger for testing
     */
    public void triggerManualScan() {
        logger.info("🚀 Manual scan triggered");
        monitorAndRemediate();
    }

    /**
     * Clear restart attempts (for testing)
     */
    public void clearRestartAttempts() {
        restartAttempts.clear();
        lastActionTime.clear();
        logger.info("🔄 Cleared restart attempt tracking");
    }

    /**
     * Debug method to check detection for specific pod
     */
    public Map<String, Object> debugPodDetection(PodInfo pod) {
        return Map.of(
            "pod", pod.getName() + "/" + pod.getNamespace(),
            "totalRestarts", getTotalRestartCount(pod),
            "allContainersReady", areAllContainersReady(pod),
            "hasWaitingContainers", hasWaitingContainers(pod),
            "isCrashLoopBackOff", isCrashLoopBackOff(pod),
            "containers", pod.getContainers().stream()
                .map(c -> Map.of(
                    "name", c.getName(),
                    "ready", c.isReady(),
                    "restartCount", c.getRestartCount(),
                    "state", c.getState()
                ))
                .collect(Collectors.toList())
        );
    }
}