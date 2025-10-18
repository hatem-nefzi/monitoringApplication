package com.example.demo.service.AutoRemediation;

import com.example.demo.model.PodInfo;
import com.example.demo.model.ContainerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 🎯 ENHANCED POD STATE DETECTOR
 * 
 * Detects all Kubernetes failure scenarios:
 * ✅ CrashLoopBackOff (pod keeps crashing)
 * ✅ OOMKilled (pod killed due to memory pressure)
 * ✅ Evicted (pod evicted by kubelet - resource pressure)
 * ✅ ImagePullBackOff (can't pull container image)
 * ✅ CreateContainerConfigError (bad pod configuration)
 * ✅ Pending too long (stuck waiting for resources)
 * 
 * Each with intelligent age checks to avoid over-reacting to new pods
 */
public class PodStateDetector {
    private static final Logger logger = LoggerFactory.getLogger(PodStateDetector.class);

    // Configuration
    private static final int MIN_POD_AGE_FOR_ACTION_MINUTES = 2;  // Wait 2 min before acting
    private static final int MIN_PENDING_TIME_MINUTES = 5;         // Pending for 5+ min = problem
    private static final int MIN_IMAGEPULL_BACKOFF_MINUTES = 3;    // ImagePullBackOff for 3+ min
    private static final int MIN_CONFIG_ERROR_WAIT_MINUTES = 1;    // Quick action for config errors

    /**
     * Detects if pod is in OOMKilled state
     * 
     * Signs:
     * - Container state shows "Waiting" or "Terminated"
     * - Reason: "OOMKilled" or "OutOfMemory"
     * - Pod restarts repeatedly due to memory
     */
    /**
 * Detects if pod is OOMKilled state
 */
public boolean isOOMKilled(PodInfo pod) {
    if (pod.getContainers() == null || pod.getContainers().isEmpty()) {
        return false;
    }

    for (ContainerInfo container : pod.getContainers()) {
        String reason = container.getReason();

        // Look for OOMKilled or OutOfMemory in reason
        if (reason != null && (reason.contains("OOMKilled") || reason.contains("OutOfMemory"))) {
            logger.debug("🚨 OOMKilled detected: {}/{}, container: {}", 
                pod.getNamespace(), pod.getName(), container.getName());
            return true;
        }

        // Also check if state is "Terminated" with OOM reason
        if ("Terminated".equals(container.getState()) && reason != null && reason.contains("OOM")) {
            logger.debug("🚨 OOMKilled (terminated): {}/{}", 
                pod.getNamespace(), pod.getName());
            return true;
        }
    }

    return false;
}

    /**
     * Detects if pod is evicted
     * 
     * Signs:
     * - Pod status: "Failed" or "Unknown"
     * - Reason contains "Evicted"
     * - Usually due to node resource pressure (disk, memory)
     */
    public boolean isEvicted(PodInfo pod) {
        if (pod.getStatus() == null) {
            return false;
        }

        // Check pod status
        if ("Evicted".equals(pod.getStatus())) {
            logger.debug("🚨 Evicted pod detected: {}/{}", 
                pod.getNamespace(), pod.getName());
            return true;
        }

        // Check pod reason
        String reason = pod.getReason();
        if (reason != null && reason.contains("Evicted")) {
            logger.debug("🚨 Evicted: {}/{} - reason: {}", 
                pod.getNamespace(), pod.getName(), reason);
            return true;
        }

        return false;
    }

    /**
     * Detects if pod is in ImagePullBackOff
     * 
     * Signs:
     * - Container state: "Waiting"
     * - Container reason: "ImagePullBackOff" or "ErrImagePull"
     * - Means: Can't pull container image from registry
     * 
     * Why not act immediately:
     * - New deployments might have temporary registry issues
     * - Wait 3+ minutes before declaring it a problem
     */
    public boolean isImagePullBackOff(PodInfo pod) {
        if (pod.getContainers() == null || pod.getContainers().isEmpty()) {
            return false;
        }

        for (ContainerInfo container : pod.getContainers()) {
            String state = container.getState();
            String reason = container.getReason();

            // ImagePullBackOff pattern
            if ("Waiting".equals(state) && reason != null && 
                (reason.contains("ImagePullBackOff") || reason.contains("ErrImagePull"))) {
                logger.debug("🚨 ImagePullBackOff: {}/{}, container: {}, reason: {}", 
                    pod.getNamespace(), pod.getName(), container.getName(), reason);
                return true;
            }
        }

        return false;
    }

    /**
     * Detects if pod has CreateContainerConfigError
     * 
     * Signs:
     * - Container state: "Waiting"
     * - Container reason: "CreateContainerConfigError" or "InvalidImageName"
     * - Means: Bad pod spec (wrong env vars, invalid volume mounts, etc)
     * 
     * Why act quickly:
     * - Config errors won't fix themselves
     * - No point retrying in 5 minutes, need manual fix
     */
    public boolean isCreateContainerConfigError(PodInfo pod) {
        if (pod.getContainers() == null || pod.getContainers().isEmpty()) {
            return false;
        }

        for (ContainerInfo container : pod.getContainers()) {
            String state = container.getState();
            String reason = container.getReason();

            if ("Waiting".equals(state) && reason != null && 
                (reason.contains("CreateContainerConfigError") || 
                 reason.contains("InvalidImageName") ||
                 reason.contains("InvalidMountPath"))) {
                logger.debug("🚨 CreateContainerConfigError: {}/{}, container: {}, reason: {}", 
                    pod.getNamespace(), pod.getName(), container.getName(), reason);
                return true;
            }
        }

        return false;
    }

    /**
     * Detects if pod is pending for too long
     * 
     * Signs:
     * - Pod status: "Pending"
     * - Pod creation time is 5+ minutes ago
     * - All containers in "Waiting" state
     * 
     * Why check age:
     * - New pods might take a moment to schedule
     * - But if pending for 5+ minutes, something is wrong (resource constraints, taints, etc)
     */
    public boolean isPendingTooLong(PodInfo pod) {
    if (!"Pending".equals(pod.getStatus())) {
        return false;
    }

    // Don't flag pods with specific failure reasons as "pending"
    // They might be transitioning to their real state
    if (pod.getReason() != null && (
        pod.getReason().contains("Evicted") || 
        pod.getReason().contains("Error") ||
        pod.getReason().contains("Backoff"))) {
        return false; // This pod has a specific issue, let other detectors handle it
    }

    // Check if pod is old enough to declare as "pending too long"
    if (!isPodOldEnough(pod, MIN_PENDING_TIME_MINUTES)) {
        logger.debug("⏳ Pod is pending but too new: {}/{}", 
            pod.getNamespace(), pod.getName());
        return false;
    }

    // Verify all containers are waiting (no specific failures)
    if (pod.getContainers() != null) {
        boolean allWaiting = pod.getContainers().stream()
            .allMatch(c -> "Waiting".equals(c.getState()));
        
        if (allWaiting) {
            logger.debug("🚨 Pending too long: {}/{} (age: 5+ minutes)", 
                pod.getNamespace(), pod.getName());
            return true;
        }
    }

    return false;
}
    /**
     * Detects CrashLoopBackOff with age check
     * 
     * Enhanced version: Only act if pod is old enough (2+ minutes)
     * 
     * Why:
     * - New pods might appear broken for first few seconds 
     * - But if they're still crashing at 2+ minutes, it's a real issue
     * - Prevents false positives on startup
     */
    public boolean isCrashLoopBackOffWithAgeCheck(PodInfo pod) {
        if (pod.getContainers() == null || pod.getContainers().isEmpty()) {
            return false;
        }

        // Check if pod is old enough
        if (!isPodOldEnough(pod, MIN_POD_AGE_FOR_ACTION_MINUTES)) {
            logger.debug("⏳ Pod appears to be crashing but too new: {}/{}", 
                pod.getNamespace(), pod.getName());
            return false;
        }

        // Now check crash loop conditions
        boolean hasHighRestarts = getTotalRestartCount(pod) >= 3;
        boolean allContainersReady = areAllContainersReady(pod);
        boolean hasWaitingContainers = hasWaitingContainers(pod);

        boolean isCrashLoop = hasHighRestarts && (!allContainersReady || hasWaitingContainers);

        if (isCrashLoop) {
            logger.debug("🚨 CrashLoopBackOff (age checked): {}/{}, restarts={}", 
                pod.getNamespace(), pod.getName(), getTotalRestartCount(pod));
        }

        return isCrashLoop;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Check if pod is old enough for remediation
     * Prevents acting on brand new pods still starting up
     */
    private boolean isPodOldEnough(PodInfo pod, int minAgeMinutes) {
        LocalDateTime createdTime = pod.getCreationTimestamp();
        
        if (createdTime == null) {
            // If we don't have creation time, assume it's old
            return true;
        }

        long ageMinutes = ChronoUnit.MINUTES.between(createdTime, LocalDateTime.now());
        return ageMinutes >= minAgeMinutes;
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

    private int getTotalRestartCount(PodInfo pod) {
        if (pod.getContainers() == null) return 0;
        return pod.getContainers().stream()
            .mapToInt(ContainerInfo::getRestartCount)
            .sum();
    }

    /**
     * Diagnostic: Get all reasons a pod might be unhealthy
     * Useful for debugging why detection triggered
     */
    public String diagnoseUnhealthyPod(PodInfo pod) {
        StringBuilder diagnosis = new StringBuilder();

        if (isOOMKilled(pod)) diagnosis.append("OOMKilled, ");
        if (isEvicted(pod)) diagnosis.append("Evicted, ");
        if (isImagePullBackOff(pod)) diagnosis.append("ImagePullBackOff, ");
        if (isCreateContainerConfigError(pod)) diagnosis.append("CreateContainerConfigError, ");
        if (isPendingTooLong(pod)) diagnosis.append("PendingTooLong, ");
        if (isCrashLoopBackOffWithAgeCheck(pod)) diagnosis.append("CrashLoopBackOff, ");

        String result = diagnosis.toString();
        return result.isEmpty() ? "No issues detected" : result.replaceAll(", $", "");
    }
}