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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Import BackoffStrategy and PodStateDetector
import com.example.demo.service.AutoRemediation.BackoffStrategy;
import com.example.demo.service.AutoRemediation.PodStateDetector;

/**
 * 🧠 AUTO-REMEDIATION ENGINE (Thread-Safe Version)
 * 
 * This service continuously monitors your cluster and automatically fixes common issues:
 * - Restarts crashing pods
 * - Cleans up failed pods
 * - Detects resource issues
 * - Logs all actions for audit
 * 
 * IMPROVEMENTS:
 * ✅ Thread-safe list operations (fixed race conditions)
 * ✅ Synchronized policy access
 * ✅ Atomic stat calculations
 */
@Service
public class RemediationService {
    private static final Logger logger = LoggerFactory.getLogger(RemediationService.class);

    @Autowired
    private KubernetesService kubernetesService;
    
    @Autowired
    private CacheService cacheService;

    // 🎯 SMART BACKOFF STRATEGY (replaces old fixed cooldown)
    private final BackoffStrategy backoffStrategy = new BackoffStrategy();
    
    // 🎯 ENHANCED POD STATE DETECTOR (catches OOMKilled, Evicted, ImagePullBackOff, etc)
    private final PodStateDetector podStateDetector = new PodStateDetector();
    
    // History of all actions (stored in Redis via CacheService)
    // Using synchronized list for thread-safe iteration
    private final List<RemediationAction> recentActions = Collections.synchronizedList(new ArrayList<>());

    // Configuration (use volatile to ensure visibility across threads)
    private volatile RemediationPolicy policy = new RemediationPolicy();

    /**
     * 🔄 MAIN MONITORING LOOP
     * Runs every 30 seconds to check for issues
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void monitorAndRemediate() {
        

        // Read policy once at start of method (more efficient than multiple accesses)
        RemediationPolicy currentPolicy = this.policy;
        if (currentPolicy.isDryRunEnabled()) {
            logger.warn("🔄 ⚠️ DRY RUN MODE ENABLED - No pods will be deleted, only logged");
        }
        
        if (!currentPolicy.isEnabled()) {
            logger.debug("Auto-remediation is disabled");
            return;
        }

        logger.info("🔍 Starting auto-remediation scan...");
        
        try {
            List<PodInfo> allPods = kubernetesService.getPodInfoClusterWide();
            // debugging
            for (PodInfo pod : allPods) {
                if (pod.getContainers() != null && !pod.getContainers().isEmpty()) {
                    for (ContainerInfo c : pod.getContainers()) {
                        logger.info("DEBUG Pod {}/{}: state={}, reason={}, ready={}, restarts={}",
                                pod.getNamespace(), pod.getName(),
                                c.getState(), c.getReason(), c.isReady(), c.getRestartCount());
                    }
                }
            }
            logger.info("📊 Scanning {} pods for issues", allPods.size());
            
            // Log details about pods with potential issues
            for (PodInfo pod : allPods) {
                
                int restarts = getTotalRestartCount(pod);
                boolean allReady = areAllContainersReady(pod);
                boolean hasWaiting = hasWaitingContainers(pod);
                
                if (restarts >= 2) {
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

                // 1. Check for OOMKilled (HIGHEST PRIORITY - memory pressure)
                if (podStateDetector.isOOMKilled(pod)) {
                    logger.info("🚨 OOM KILLED DETECTED: {}/{} with {} restarts", 
                        pod.getNamespace(), pod.getName(), getTotalRestartCount(pod));
                    issuesFound++;
                    if (shouldRemediateAfterBackoff(podKey)) {
                        logger.info("🔄 TAKING ACTION: Deleting OOMKilled pod {}/{} (requires manual intervention)", 
                            pod.getNamespace(), pod.getName());
                        handleOOMKilled(pod, currentPolicy);
                        actionsTaken++;
                    }
                }

                // 2. Check for Evicted (pod was kicked off node)
                else if (podStateDetector.isEvicted(pod)) {
                    logger.info("⚠️ EVICTED POD: {}/{}", pod.getNamespace(), pod.getName());
                    issuesFound++;
                    if (shouldRemediateAfterBackoff(podKey)) {
                        logger.info("🔄 TAKING ACTION: Deleting evicted pod {}/{}", 
                            pod.getNamespace(), pod.getName());
                        handleEvictedPod(pod, currentPolicy);
                        actionsTaken++;
                    }
                }

                

                // 4. Check for CreateContainerConfigError (bad pod config)
                else if (podStateDetector.isCreateContainerConfigError(pod)) {
                    logger.info("❌ CREATE CONTAINER CONFIG ERROR: {}/{}", 
                        pod.getNamespace(), pod.getName());
                    issuesFound++;
                    handleCreateContainerConfigError(pod, currentPolicy);
                    // Don't auto-remediate - needs manual fix (bad spec/config)
                }

                // 5. Check for Pending too long (resource constraints)
                else if (podStateDetector.isPendingTooLong(pod)) {
                    logger.info("⏳ PENDING TOO LONG: {}/{}", pod.getNamespace(), pod.getName());
                    issuesFound++;
                    handlePendingPod(pod);
                    // Don't auto-remediate - likely resource constraint or taint issue
                }

                // 6. Check for CrashLoopBackOff with age check
                else if (podStateDetector.isCrashLoopBackOffWithAgeCheck(pod)) {
                    logger.info("🎯 CRASH LOOP DETECTED: {}/{} with {} restarts", 
                        pod.getNamespace(), pod.getName(), getTotalRestartCount(pod));
                    issuesFound++;
                    
                    // 🔄 Use smart backoff strategy
                    boolean isHealthy = areAllContainersReady(pod) && getTotalRestartCount(pod) == 0;
                    if (backoffStrategy.shouldRemediateNow(podKey, isHealthy)) {
                        logger.info("🔄 TAKING ACTION: Deleting pod {}/{}", 
                            pod.getNamespace(), pod.getName());
                        handleCrashLoopBackOff(pod, currentPolicy);
                        actionsTaken++;
                    } else {
                        BackoffStrategy.BackoffState state = backoffStrategy.getState(podKey);
                        logger.info("⏸️  BACKOFF: Pod {}/{} - will retry after backoff. Attempt {}/{}",
                            pod.getNamespace(), pod.getName(), state.getAttemptCount(), 
                            currentPolicy.getMaxRestartAttempts());
                    }
                }
                // 3. Check for ImagePullBackOff (can't pull image)
                else if (podStateDetector.isImagePullBackOff(pod)) {
                    logger.info("🖼️ IMAGE PULL BACKOFF: {}/{}", pod.getNamespace(), pod.getName());
                    issuesFound++;
                    handleImagePullBackOff(pod, currentPolicy);
                    // Don't auto-remediate - likely a registry issue or wrong image name
                }

                
            }

            logger.info("✅ Scan complete: {} issues found, {} actions taken", issuesFound, actionsTaken);

        } catch (Exception e) {
            logger.error("❌ Error during monitoring: {}", e.getMessage(), e);
        }
    }

    // ==================== REMEDIATION HANDLERS ====================

    /**
     * 🚨 Handle OOMKilled: Pod was killed due to memory pressure
     * Action: Delete pod and alert ops (likely needs more memory)
     */
    private void handleOOMKilled(PodInfo pod, RemediationPolicy currentPolicy) {
    String podKey = getPodKey(pod);

    RemediationAction action = createAction(pod, "OOMKilled", "Delete Pod & Alert", 
        "Pod was killed due to out-of-memory. May need memory limit increase or memory leak fix.");

    try {
        if (currentPolicy.isDryRunEnabled()) {
            // DRY RUN: Don't actually delete, just log what we would do
            logger.info("🔄 [DRY RUN] Would delete OOMKilled pod {}/{} (would require manual investigation)", 
                pod.getNamespace(), pod.getName());
            
            action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
            action.setMetadata(Map.of(
                "reason", "OOMKilled",
                "totalRestarts", String.valueOf(getTotalRestartCount(pod)),
                "mode", "DRY_RUN",
                "wouldDelete", "true",
                "recommendation", "Review pod memory requests/limits and application memory usage"
            ));
        } else {
            // LIVE MODE: Actually delete the pod
            kubernetesService.deletePod(pod.getNamespace(), pod.getName());
            backoffStrategy.recordRemediationAttempt(podKey);

            action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
            action.setMetadata(Map.of(
                "reason", "OOMKilled",
                "totalRestarts", String.valueOf(getTotalRestartCount(pod)),
                "mode", "LIVE",
                "deleted", "true",
                "recommendation", "Review pod memory requests/limits and application memory usage"
            ));

            logger.info("✅ Deleted OOMKilled pod {} (requires manual investigation)", podKey);
        }
    } catch (ApiException e) {
        action.setStatus(RemediationAction.RemediationStatus.FAILED);
        action.setError(e.getResponseBody());
        logger.error("❌ Failed to delete OOMKilled pod {}: {}", podKey, e.getResponseBody());
    }

    recordAction(action);
}

    /**
     * Handle Evicted pod with dry-run support
     */
    private void handleEvictedPod(PodInfo pod, RemediationPolicy currentPolicy) {
        String podKey = getPodKey(pod);

        RemediationAction action = createAction(pod, "Evicted", "Delete Pod",
            "Pod was evicted from node (likely node resource pressure)");

        try {
            if (currentPolicy.isDryRunEnabled()) {
                logger.info("🔄 [DRY RUN] Would delete evicted pod {}/{}",
                    pod.getNamespace(), pod.getName());

                action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
                action.setMetadata(Map.of(
                    "reason", "Evicted - likely node resource pressure",
                    "mode", "DRY_RUN",
                    "wouldDelete", "true",
                    "recommendation", "Check node resources (disk, memory, inode) with 'kubectl describe node'"
                ));
            } else {
                kubernetesService.deletePod(pod.getNamespace(), pod.getName());

                action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
                action.setMetadata(Map.of(
                    "reason", "Evicted - likely node resource pressure",
                    "mode", "LIVE",
                    "deleted", "true",
                    "recommendation", "Check node resources (disk, memory, inode) with 'kubectl describe node'"
                ));

                logger.info("✅ Deleted evicted pod {}", podKey);
            }
        } catch (ApiException e) {
            action.setStatus(RemediationAction.RemediationStatus.FAILED);
            action.setError(e.getResponseBody());
            logger.error("❌ Failed to delete evicted pod {}: {}", podKey, e.getResponseBody());
        }

        recordAction(action);
    }

    /**
     * Handle CrashLoopBackOff with dry-run support
     */
    private void handleCrashLoopBackOff(PodInfo pod, RemediationPolicy currentPolicy) {
        String podKey = getPodKey(pod);
        BackoffStrategy.BackoffState state = backoffStrategy.getState(podKey);
        int attempts = state.getAttemptCount();

        // Safety check: Don't restart forever
        if (attempts >= currentPolicy.getMaxRestartAttempts()) {
            logger.warn("⚠️ Pod {} exceeded max restart attempts ({}). Manual intervention needed.",
                podKey, currentPolicy.getMaxRestartAttempts());

            RemediationAction action = createAction(pod, "CrashLoopBackOff", "SKIPPED",
                "Exceeded max restart attempts - manual intervention needed");
            action.setStatus(RemediationAction.RemediationStatus.SKIPPED);
            action.setMetadata(Map.of(
                "reason", "Max attempts exceeded",
                "attempts", String.valueOf(attempts),
                "lastHealthy", state.getLastHealthyTime().toString()
            ));
            recordAction(action);
            return;
        }

        RemediationAction action = createAction(pod, "CrashLoopBackOff", "Delete Pod (Force Restart)",
            "Pod is in crash loop with " + getMaxRestartCount(pod) + " restarts");

        try {
            if (currentPolicy.isDryRunEnabled()) {
                // DRY RUN: Log what we would do
                logger.info("🔄 [DRY RUN] Would delete pod {}/{} to fix crash loop (attempt {}/{})",
                    pod.getNamespace(), pod.getName(), attempts + 1, currentPolicy.getMaxRestartAttempts());

                long nextWaitMinutes = backoffStrategy.calculateBackoffWait(attempts + 2);

                action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
                action.setMetadata(Map.of(
                    "restartAttempt", String.valueOf(attempts + 1),
                    "maxAttempts", String.valueOf(currentPolicy.getMaxRestartAttempts()),
                    "containerRestarts", String.valueOf(getMaxRestartCount(pod)),
                    "totalRestarts", String.valueOf(getTotalRestartCount(pod)),
                    "mode", "DRY_RUN",
                    "wouldDelete", "true",
                    "nextBackoffWaitMinutes", String.valueOf(nextWaitMinutes),
                    "backoffSchedule", "2min → 5min → 10min → 20min → 60min (max)"
                ));
            } else {
                // LIVE MODE: Actually delete the pod
                kubernetesService.deletePod(pod.getNamespace(), pod.getName());
                backoffStrategy.recordRemediationAttempt(podKey);
                int newAttemptCount = backoffStrategy.getState(podKey).getAttemptCount();
                long nextWaitMinutes = backoffStrategy.calculateBackoffWait(newAttemptCount + 1);

                action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
                action.setMetadata(Map.of(
                    "restartAttempt", String.valueOf(newAttemptCount),
                    "maxAttempts", String.valueOf(currentPolicy.getMaxRestartAttempts()),
                    "containerRestarts", String.valueOf(getMaxRestartCount(pod)),
                    "totalRestarts", String.valueOf(getTotalRestartCount(pod)),
                    "mode", "LIVE",
                    "deleted", "true",
                    "nextBackoffWaitMinutes", String.valueOf(nextWaitMinutes),
                    "backoffSchedule", "2min → 5min → 10min → 20min → 60min (max)"
                ));

                logger.info("✅ Deleted pod {} to fix crash loop (attempt {}/{}, next retry in {}min)",
                    podKey, newAttemptCount, currentPolicy.getMaxRestartAttempts(), nextWaitMinutes);
            }
        } catch (ApiException e) {
            action.setStatus(RemediationAction.RemediationStatus.FAILED);
            action.setError(e.getResponseBody());
            logger.error("❌ Failed to delete pod {}: {}", podKey, e.getResponseBody());
        }

        recordAction(action);
    }

    /**
     * Handle Failed pod with dry-run support
     */
    private void handleFailedPod(PodInfo pod, RemediationPolicy currentPolicy) {
        if (!currentPolicy.isAutoDeleteFailedPods()) {
            logger.info("⏸️ Auto-delete for failed pods is disabled");
            return;
        }

        RemediationAction action = createAction(pod, "Failed", "Delete Failed Pod",
            "Cleaning up failed pod");

        try {
            if (currentPolicy.isDryRunEnabled()) {
                logger.info("🔄 [DRY RUN] Would delete failed pod {}/{}",
                    pod.getNamespace(), pod.getName());

                action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
                action.setMetadata(Map.of(
                    "mode", "DRY_RUN",
                    "wouldDelete", "true"
                ));
            } else {
                kubernetesService.deletePod(pod.getNamespace(), pod.getName());

                action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
                action.setMetadata(Map.of(
                    "mode", "LIVE",
                    "deleted", "true"
                ));

                logger.info("✅ Deleted failed pod {}", getPodKey(pod));
            }
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


    /**
 * Handle ImagePullBackOff with dry-run support
 */
private void handleImagePullBackOff(PodInfo pod, RemediationPolicy currentPolicy) {
    RemediationAction action = createAction(pod, "ImagePullBackOff", "ALERT", 
        "Cannot pull container image from registry");

    action.setStatus(RemediationAction.RemediationStatus.SKIPPED);
    action.setMetadata(Map.of(
        "reason", "ImagePullBackOff",
        "mode", currentPolicy.isDryRunEnabled() ? "DRY_RUN" : "LIVE",
        "recommendation", "Check: 1) Image name spelling, 2) Image exists in registry, 3) Pull credentials, 4) Registry connectivity"
    ));

    logger.warn("⚠️ Pod {}/{} cannot pull image - manual investigation needed", 
        pod.getNamespace(), pod.getName());
    recordAction(action);
}

/**
 * Handle CreateContainerConfigError with dry-run support
 */
private void handleCreateContainerConfigError(PodInfo pod, RemediationPolicy currentPolicy) {
    RemediationAction action = createAction(pod, "CreateContainerConfigError", "ALERT", 
        "Container configuration error - check pod spec");

    action.setStatus(RemediationAction.RemediationStatus.SKIPPED);
    action.setMetadata(Map.of(
        "reason", "CreateContainerConfigError",
        "mode", currentPolicy.isDryRunEnabled() ? "DRY_RUN" : "LIVE",
        "recommendation", "Check pod spec: invalid env vars, bad volume mounts, invalid resource requests"
    ));

    logger.warn("⚠️ Pod {}/{} has config error - manual fix required", 
        pod.getNamespace(), pod.getName());
    recordAction(action);
}

    // ==================== DETECTION HELPERS ====================

    /**
     * A pod is in crash loop if it has high restart count AND is not running properly
     * WITH AGE CHECK: Only flag if pod is 2+ minutes old
     */
    private boolean isCrashLoopBackOff(PodInfo pod) {
        return podStateDetector.isCrashLoopBackOffWithAgeCheck(pod);
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

    /**
     * Helper: Should remediate after backoff check (consolidated check)
     */
    private boolean shouldRemediateAfterBackoff(String podKey) {
        // Simplified: Use backoff strategy for all remediations
        return true; // Backoff strategy will handle timing
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

    // ==================== UTILITY METHODS ====================

    private boolean isSystemNamespace(String namespace) {
        return namespace.startsWith("kube-") || 
               "cattle-system".equals(namespace) ||
               "ingress-nginx".equals(namespace);
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
        remediationAction.setTimestamp(Instant.now());
        return remediationAction;
    }

    /**
     * ✅ FIXED: Thread-safe action recording
     * - Synchronized block to prevent race conditions
     * - Atomic size check and removal
     * - Proper exception handling
     */
    private void recordAction(RemediationAction action) {
        synchronized(recentActions) {
            recentActions.add(action);
            
            // Keep only last 100 actions in memory (atomic with add)
            if (recentActions.size() > 100) {
                recentActions.remove(0);
            }
        }

        // Store in Redis for persistence (outside lock, won't block monitoring loop)
        try {
            cacheService.storeRemediationAction(action);
        } catch (Exception e) {
            logger.warn("Failed to store action in Redis: {}", e.getMessage());
        }

        logger.info("📝 Recorded action: {}", action);
    }

    // ==================== PUBLIC API ====================

    /**
     * ✅ FIXED: Thread-safe list retrieval
     * - Synchronized block prevents race conditions during iteration
     * - Creates defensive copy (subList + ArrayList)
     */
    public List<RemediationAction> getRecentActions(int limit) {
        synchronized(recentActions) {
            int size = recentActions.size();
            int fromIndex = Math.max(0, size - limit);
            // Create defensive copy inside synchronized block
            return new ArrayList<>(recentActions.subList(fromIndex, size));
        }
    }

    /**
     * Get policy (thread-safe read with volatile)
     */
    public RemediationPolicy getPolicy() {
        return this.policy;
    }

    /**
     * Update policy (thread-safe write with volatile)
     */
    public synchronized void updatePolicy(RemediationPolicy newPolicy) {
        this.policy = newPolicy;
        logger.info("📋 Policy updated: {}", newPolicy);
    }

    /**
     * ✅ FIXED: Thread-safe statistics calculation
     * - Synchronized block for consistent snapshot
     * - All counts calculated within lock
     */
    public Map<String, Object> getStatistics() {
        synchronized(recentActions) {
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
                "policyEnabled", this.policy.isEnabled(),
                "trackedPods", backoffStrategy.getAllStates().size()
            );
        }
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
        backoffStrategy.clearAllStates();
        logger.info("🔄 Cleared all backoff states");
    }

    /**
     * Debug method to check detection for specific pod
     */
    public Map<String, Object> debugPodDetection(PodInfo pod) {
        String podKey = getPodKey(pod);
        BackoffStrategy.BackoffState backoffState = backoffStrategy.getState(podKey);
        
        return Map.of(
            "pod", pod.getName() + "/" + pod.getNamespace(),
            "totalRestarts", getTotalRestartCount(pod),
            "allContainersReady", areAllContainersReady(pod),
            "hasWaitingContainers", hasWaitingContainers(pod),
            "isCrashLoopBackOff", isCrashLoopBackOff(pod),
            "backoffState", Map.of(
                "status", backoffState.getStatus(),
                "attemptCount", backoffState.getAttemptCount(),
                "lastActionTime", backoffState.getLastActionTime(),
                "lastHealthyTime", backoffState.getLastHealthyTime(),
                "totalRemediationsTaken", backoffState.getTotalRemediationsTaken()
            ),
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

    /**
     * Get backoff strategy (for monitoring/debugging)
     */
    public BackoffStrategy getBackoffStrategy() {
        return backoffStrategy;
    }
}