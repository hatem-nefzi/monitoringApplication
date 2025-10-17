package com.example.demo.service.AutoRemediation;

import com.example.demo.model.AutoRemediation.RemediationAction;
import com.example.demo.model.AutoRemediation.RemediationPolicy;
import com.example.demo.model.PodInfo;
import com.example.demo.model.ContainerInfo;
import com.example.demo.service.KubernetesService;
import com.example.demo.service.CacheService;
import io.kubernetes.client.openapi.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * COMPREHENSIVE TEST SUITE FOR REMEDIATIONSERVICE
 * 
 * Tests cover:
 * ✅ Thread-safety of concurrent operations
 * ✅ Detection logic (crash loops, high restarts, etc)
 * ✅ Action recording and history
 * ✅ Policy management
 * ✅ Edge cases
 */
@DisplayName("RemediationService Test Suite")
class RemediationServiceTest {

    private RemediationService remediationService;

    @Mock
    private KubernetesService kubernetesService;

    @Mock
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        remediationService = new RemediationService();
        
        // Use reflection to inject mocked dependencies
        ReflectionTestUtils.setField(remediationService, "kubernetesService", kubernetesService);
        ReflectionTestUtils.setField(remediationService, "cacheService", cacheService);
    }

    // ==================== PART 1: THREAD-SAFETY TESTS ====================

    @Test
    @DisplayName("Thread-Safety: Multiple threads can record actions simultaneously without crashes")
    void testThreadSafetyOfRecordingActions() throws InterruptedException {
        // Setup
        int numThreads = 10;
        int actionsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1); // Wait for all threads to start together
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        // Execute: Multiple threads recording actions simultaneously
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to start together
                    
                    for (int j = 0; j < actionsPerThread; j++) {
                        RemediationAction action = new RemediationAction(
                            "pod-" + threadId,
                            "namespace-" + threadId,
                            "TestIssue",
                            "TestAction",
                            "Thread " + threadId + " action " + j
                        );
                        
                        // Call private method through reflection
                        ReflectionTestUtils.invokeMethod(remediationService, "recordAction", action);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Signal all threads to start
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);

        // Verify: No exceptions thrown, all actions recorded
        assertTrue(completed, "Thread test did not complete in time");
        
        // Because we keep only 100 actions, we should have 100 (not 500)
        List<RemediationAction> actions = remediationService.getRecentActions(1000);
        assertEquals(100, actions.size(), "Should have exactly 100 actions (limited to 100)");
        
        executor.shutdown();
    }

    @Test
    @DisplayName("Thread-Safety: getRecentActions() doesn't throw IndexOutOfBoundsException under concurrent modification")
    void testThreadSafeGetRecentActions() throws InterruptedException {
        // Setup
        int numReaders = 5;
        int numWriters = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numReaders + numWriters);
        CountDownLatch endLatch = new CountDownLatch(numReaders + numWriters);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // Writers: Constantly adding actions
        for (int i = 0; i < numWriters; i++) {
            final int writerId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        RemediationAction action = new RemediationAction(
                            "writer-pod-" + writerId,
                            "default",
                            "Issue",
                            "Action",
                            "Writer " + writerId + " action " + j
                        );
                        ReflectionTestUtils.invokeMethod(remediationService, "recordAction", action);
                        Thread.sleep(1); // Small delay to increase contention
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Readers: Constantly reading actions
        for (int i = 0; i < numReaders; i++) {
            final int readerId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        // This should NOT throw IndexOutOfBoundsException
                        List<RemediationAction> actions = remediationService.getRecentActions(50);
                        assertNotNull(actions);
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                    fail("getRecentActions() threw exception: " + e.getClass().getSimpleName());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "Concurrent test did not complete in time");
        assertEquals(0, exceptionCount.get(), "No exceptions should be thrown");
        
        executor.shutdown();
    }

    @Test
    @DisplayName("Thread-Safety: getStatistics() returns consistent results under concurrent access")
    void testThreadSafeStatistics() throws InterruptedException {
        // Add some initial actions
        for (int i = 0; i < 50; i++) {
            RemediationAction action = new RemediationAction("pod-" + i, "default", "Issue", "Action", "Reason");
            if (i % 2 == 0) {
                action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
            } else {
                action.setStatus(RemediationAction.RemediationStatus.FAILED);
            }
            ReflectionTestUtils.invokeMethod(remediationService, "recordAction", action);
        }

        // Execute: Multiple threads reading stats simultaneously
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    Map<String, Object> stats = remediationService.getStatistics();
                    results.add(stats);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Stats test did not complete in time");

        // Verify: All results are consistent
        for (Map<String, Object> stats : results) {
            int total = (int) stats.get("totalActions");
            long successful = (long) stats.get("successfulActions");
            long failed = (long) stats.get("failedActions");
            
            // Consistency check: successful + failed should be <= total
            assertTrue(successful + failed <= total, 
                "Inconsistent stats: " + successful + " + " + failed + " > " + total);
        }

        executor.shutdown();
    }

    // ==================== PART 2: DETECTION LOGIC TESTS ====================

    @Test
    @DisplayName("Detection: Identifies CrashLoopBackOff correctly")
    void testCrashLoopDetection() {
        // Setup: Pod with high restarts and waiting containers
        PodInfo pod = createTestPod("namespace", "crashing-pod", 3);
        ContainerInfo container = pod.getContainers().get(0);
        container.setRestartCount(5);
        container.setState("Waiting");
        container.setReady(false);

        // Execute
        boolean isCrashLoop = (Boolean) ReflectionTestUtils.invokeMethod(
            remediationService, "isCrashLoopBackOff", pod);

        // Verify
        assertTrue(isCrashLoop, "Should detect crash loop");
    }

    @Test
    @DisplayName("Detection: Does NOT identify healthy pod as CrashLoopBackOff")
    void testHealthyPodNotCrashLoop() {
        // Setup: Pod with high restarts but all containers ready
        PodInfo pod = createTestPod("namespace", "healthy-pod", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setRestartCount(5);
        container.setState("Running");
        container.setReady(true);

        // Execute
        boolean isCrashLoop = (Boolean) ReflectionTestUtils.invokeMethod(
            remediationService, "isCrashLoopBackOff", pod);

        // Verify
        assertFalse(isCrashLoop, "Should NOT detect crash loop for healthy pod");
    }

    @Test
    @DisplayName("Detection: Detects high restart count")
    void testHighRestartCountDetection() {
        PodInfo pod = createTestPod("namespace", "high-restart-pod", 1);
        pod.getContainers().get(0).setRestartCount(10);

        // Execute
        boolean hasHigh = (Boolean) ReflectionTestUtils.invokeMethod(
            remediationService, "hasHighRestartCount", pod, 5);

        // Verify
        assertTrue(hasHigh, "Should detect high restart count");
    }

    @Test
    @DisplayName("Detection: Calculates total restart count correctly")
    void testTotalRestartCountCalculation() {
        // Setup: Pod with multiple containers with different restart counts
        PodInfo pod = createTestPod("namespace", "multi-container-pod", 3);
        pod.getContainers().get(0).setRestartCount(2);
        pod.getContainers().get(1).setRestartCount(3);
        pod.getContainers().get(2).setRestartCount(5);

        // Execute
        int total = (Integer) ReflectionTestUtils.invokeMethod(
            remediationService, "getTotalRestartCount", pod);

        // Verify
        assertEquals(10, total, "Total restart count should be 2+3+5=10");
    }

    @Test
    @DisplayName("Detection: Returns 0 for pod with no containers")
    void testEmptyPodContainers() {
        PodInfo pod = new PodInfo();
        pod.setName("empty-pod");
        pod.setNamespace("namespace");
        pod.setContainers(new ArrayList<>()); // Empty container list

        // Execute
        int total = (Integer) ReflectionTestUtils.invokeMethod(
            remediationService, "getTotalRestartCount", pod);

        // Verify
        assertEquals(0, total, "Should return 0 for pod with no containers");
    }

    // ==================== PART 3: ACTION RECORDING TESTS ====================

    @Test
    @DisplayName("Recording: Stores action with correct status")
    void testRecordActionStatus() {
        RemediationAction action = new RemediationAction(
            "test-pod", "default", "TestIssue", "TestAction", "Test reason"
        );
        action.setStatus(RemediationAction.RemediationStatus.SUCCESS);

        // Execute
        ReflectionTestUtils.invokeMethod(remediationService, "recordAction", action);

        // Verify
        List<RemediationAction> actions = remediationService.getRecentActions(10);
        assertEquals(1, actions.size());
        assertEquals(RemediationAction.RemediationStatus.SUCCESS, actions.get(0).getStatus());
    }

    @Test
    @DisplayName("Recording: Maintains history limit of 100 actions")
    void testRecordActionHistoryLimit() {
        // Add 150 actions
        for (int i = 0; i < 150; i++) {
            RemediationAction action = new RemediationAction(
                "pod-" + i, "default", "Issue", "Action", "Reason"
            );
            ReflectionTestUtils.invokeMethod(remediationService, "recordAction", action);
        }

        // Verify: Only 100 kept
        List<RemediationAction> actions = remediationService.getRecentActions(1000);
        assertEquals(100, actions.size(), "Should maintain limit of 100 actions");
    }

    @Test
    @DisplayName("Recording: Returns most recent N actions")
    void testGetRecentActionsLimit() {
        // Add 50 actions
        for (int i = 0; i < 50; i++) {
            RemediationAction action = new RemediationAction(
                "pod-" + i, "default", "Issue", "Action", "Reason " + i
            );
            ReflectionTestUtils.invokeMethod(remediationService, "recordAction", action);
        }

        // Execute: Get last 10
        List<RemediationAction> recent = remediationService.getRecentActions(10);

        // Verify
        assertEquals(10, recent.size(), "Should return requested limit");
        assertEquals("Reason 49", recent.get(9).getReason(), "Should return most recent actions");
    }

    // ==================== PART 4: POLICY MANAGEMENT TESTS ====================

    @Test
    @DisplayName("Policy: Updates and reads policy correctly")
    void testPolicyUpdate() {
        RemediationPolicy newPolicy = new RemediationPolicy();
        newPolicy.setEnabled(false);
        newPolicy.setMaxRestartAttempts(5);

        // Execute
        remediationService.updatePolicy(newPolicy);

        // Verify
        RemediationPolicy retrieved = remediationService.getPolicy();
        assertFalse(retrieved.isEnabled());
        assertEquals(5, retrieved.getMaxRestartAttempts());
    }

    @Test
    @DisplayName("Policy: Default policy is properly initialized")
    void testDefaultPolicy() {
        RemediationPolicy policy = remediationService.getPolicy();

        assertTrue(policy.isEnabled(), "Should be enabled by default");
        assertTrue(policy.isAutoRestartCrashingPods(), "Should auto-restart by default");
        assertEquals(3, policy.getMaxRestartAttempts(), "Default max attempts should be 3");
        assertFalse(policy.isAutoDeleteFailedPods(), "Should NOT auto-delete failed pods by default");
    }

    // ==================== PART 5: STATISTICS TESTS ====================

    @Test
    @DisplayName("Statistics: Calculates success/failure counts correctly")
    void testStatisticsCalculation() {
        // Add 30 successful and 20 failed actions
        for (int i = 0; i < 30; i++) {
            RemediationAction action = new RemediationAction("pod", "ns", "Issue", "Action", "Reason");
            action.setStatus(RemediationAction.RemediationStatus.SUCCESS);
            ReflectionTestUtils.invokeMethod(remediationService, "recordAction", action);
        }
        for (int i = 0; i < 20; i++) {
            RemediationAction action = new RemediationAction("pod", "ns", "Issue", "Action", "Reason");
            action.setStatus(RemediationAction.RemediationStatus.FAILED);
            ReflectionTestUtils.invokeMethod(remediationService, "recordAction", action);
        }

        // Execute
        Map<String, Object> stats = remediationService.getStatistics();

        // Verify
        assertEquals(50, stats.get("totalActions"));
        assertEquals(30L, stats.get("successfulActions"));
        assertEquals(20L, stats.get("failedActions"));
    }

    // ==================== PART 6: EDGE CASES & ERROR HANDLING ====================

    @Test
    @DisplayName("Edge Case: Handling null pod containers")
    void testNullContainers() {
        PodInfo pod = new PodInfo();
        pod.setName("null-container-pod");
        pod.setNamespace("default");
        pod.setContainers(null); // Null containers

        // Execute - should not throw exception
        boolean isCrashLoop = (Boolean) ReflectionTestUtils.invokeMethod(
            remediationService, "isCrashLoopBackOff", pod);

        // Verify
        assertFalse(isCrashLoop, "Should handle null containers gracefully");
    }

    @Test
    @DisplayName("Edge Case: Handling empty recent actions list")
    void testEmptyRecentActions() {
        // Execute
        List<RemediationAction> actions = remediationService.getRecentActions(10);

        // Verify
        assertNotNull(actions);
        assertEquals(0, actions.size(), "Should return empty list");
    }

    @Test
    @DisplayName("Edge Case: Requesting more actions than available")
    void testRequestMoreActionsThanAvailable() {
        // Add 5 actions
        for (int i = 0; i < 5; i++) {
            RemediationAction action = new RemediationAction("pod", "ns", "Issue", "Action", "Reason");
            ReflectionTestUtils.invokeMethod(remediationService, "recordAction", action);
        }

        // Execute: Request 100 but only 5 available
        List<RemediationAction> actions = remediationService.getRecentActions(100);

        // Verify
        assertEquals(5, actions.size(), "Should return only available actions");
    }

    // ==================== HELPER METHODS ====================

    /**
     * Creates a test pod with N containers
     */
    private PodInfo createTestPod(String namespace, String name, int numContainers) {
        PodInfo pod = new PodInfo();
        pod.setNamespace(namespace);
        pod.setName(name);
        pod.setStatus("Running");

        List<ContainerInfo> containers = new ArrayList<>();
        for (int i = 0; i < numContainers; i++) {
            ContainerInfo container = new ContainerInfo();
            container.setName("container-" + i);
            container.setState("Running");
            container.setReady(true);
            container.setRestartCount(0);
            containers.add(container);
        }
        pod.setContainers(containers);

        return pod;
    }
}