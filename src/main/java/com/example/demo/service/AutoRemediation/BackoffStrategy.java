package com.example.demo.service.AutoRemediation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🎯 SMART EXPONENTIAL BACKOFF STRATEGY
 * 
 * Instead of fixed 2-minute cooldown, this implements intelligent backoff:
 * 
 * Scenario: Pod keeps crashing
 * ├─ Attempt 1: Act immediately (0 minutes)
 * ├─ Attempt 2: Wait 2 minutes before retry
 * ├─ Attempt 3: Wait 5 minutes before retry  (exponential: 2 * 2.5)
 * ├─ Attempt 4: Wait 10 minutes before retry (exponential: 5 * 2)
 * └─ Attempt 5: Would wait 20 minutes (but we're at max=3, so give up)
 * 
 * BUT ALSO:
 * - If pod becomes healthy (all containers ready, 0 restarts) → RESET counter to 0
 * - Tracks when pod was last healthy (for monitoring)
 * - Prevents infinite restart loops
 */
public class BackoffStrategy {

    /**
     * Tracks backoff state for each pod
     * Key: "namespace/podname"
     * Value: BackoffState with attempt count, timings, health history
     */
    private final Map<String, BackoffState> backoffStates = new ConcurrentHashMap<>();

    /**
     * Determines if we should act on a pod right now
     * 
     * @param podKey "namespace/podname"
     * @param isCurrentlyHealthy Is the pod healthy RIGHT NOW?
     * @return true if we should act, false if we should wait
     */
    public boolean shouldRemediateNow(String podKey, boolean isCurrentlyHealthy) {
        BackoffState state = backoffStates.getOrDefault(podKey, new BackoffState());

        // Pod is healthy: Reset attempt counter
        if (isCurrentlyHealthy) {
            if (state.getAttemptCount() > 0) {
                // Pod recovered! Reset everything
                BackoffState newState = new BackoffState();
                newState.setLastHealthyTime(LocalDateTime.now());
                backoffStates.put(podKey, newState);
                return false; // Pod is fine, no remediation needed
            }
            return false; // Already healthy, nothing to do
        }

        // Pod is unhealthy: Check if we should retry
        int attempts = state.getAttemptCount();

        // First attempt: Always act immediately
        if (attempts == 0) {
            return true;
        }

        // Check if cooldown period has passed
        LocalDateTime lastActionTime = state.getLastActionTime();
        if (lastActionTime == null) {
            return true; // First time tracking this state
        }

        long minutesElapsed = ChronoUnit.MINUTES.between(lastActionTime, LocalDateTime.now());
        long requiredWait = calculateBackoffWait(attempts);

        return minutesElapsed >= requiredWait;
    }

    /**
     * Calculates exponential backoff wait time
     * 
     * Attempt 1: 0 minutes (act immediately)
     * Attempt 2: 2 minutes
     * Attempt 3: 5 minutes (2 * 2.5)
     * Attempt 4: 10 minutes (5 * 2)
     * Attempt 5: 20 minutes (10 * 2)
     * 
     * Formula: base * (multiplier ^ (attempt - 1)), capped at maxWait
     */
    public long calculateBackoffWait(int attemptCount) {
        if (attemptCount <= 1) {
            return 0; // First attempt: no wait
        }

        // Exponential: 2, 5, 10, 20, 40...
        long wait = 2; // Base wait: 2 minutes
        for (int i = 2; i < attemptCount; i++) {
            if (i == 2) {
                wait = 5; // 2 * 2.5 ≈ 5
            } else {
                wait = wait * 2; // Then double each time
            }
        }

        // Cap at 60 minutes max (don't wait forever)
        return Math.min(wait, 60);
    }

    /**
     * Record that we took an action
     * Increments attempt counter
     */
    public void recordRemediationAttempt(String podKey) {
        BackoffState state = backoffStates.getOrDefault(podKey, new BackoffState());
        state.incrementAttemptCount();
        state.setLastActionTime(LocalDateTime.now());
        backoffStates.put(podKey, state);
    }

    /**
     * Get current backoff state for a pod (for monitoring/debugging)
     */
    public BackoffState getState(String podKey) {
        return backoffStates.getOrDefault(podKey, new BackoffState());
    }

    /**
     * Get all backoff states (for dashboard/monitoring)
     */
    public Map<String, BackoffState> getAllStates() {
        return new ConcurrentHashMap<>(backoffStates);
    }

    /**
     * Clear backoff state (for testing or manual reset)
     */
    public void clearState(String podKey) {
        backoffStates.remove(podKey);
    }

    public void clearAllStates() {
        backoffStates.clear();
    }

    // ==================== INNER CLASS: BACKOFF STATE ====================

    /**
     * Tracks the backoff state for a single pod
     */
    public static class BackoffState {
        private int attemptCount = 0;
        private LocalDateTime lastActionTime;
        private LocalDateTime lastHealthyTime;
        private int totalRemediationsTaken = 0; // For analytics

        public BackoffState() {
            this.lastHealthyTime = LocalDateTime.now(); // Assume it was healthy at creation
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public void setAttemptCount(int attemptCount) {
            this.attemptCount = attemptCount;
        }

        public void incrementAttemptCount() {
            this.attemptCount++;
            this.totalRemediationsTaken++;
        }

        public LocalDateTime getLastActionTime() {
            return lastActionTime;
        }

        public void setLastActionTime(LocalDateTime lastActionTime) {
            this.lastActionTime = lastActionTime;
        }

        public LocalDateTime getLastHealthyTime() {
            return lastHealthyTime;
        }

        public void setLastHealthyTime(LocalDateTime lastHealthyTime) {
            this.lastHealthyTime = lastHealthyTime;
        }

        public int getTotalRemediationsTaken() {
            return totalRemediationsTaken;
        }

        /**
         * Returns human-readable status
         */
        public String getStatus() {
            if (attemptCount == 0) {
                return "HEALTHY";
            }
            return "UNSTABLE (attempt " + attemptCount + ")";
        }

        @Override
        public String toString() {
            return "BackoffState{" +
                    "attempts=" + attemptCount +
                    ", status=" + getStatus() +
                    ", lastAction=" + lastActionTime +
                    ", lastHealthy=" + lastHealthyTime +
                    ", totalRemediated=" + totalRemediationsTaken +
                    '}';
        }
    }
}