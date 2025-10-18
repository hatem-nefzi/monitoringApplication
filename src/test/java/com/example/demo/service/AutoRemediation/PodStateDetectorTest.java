package com.example.demo.service.AutoRemediation;

import com.example.demo.model.PodInfo;
import com.example.demo.model.ContainerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ENHANCED POD STATE DETECTION TESTS
 * 
 * Tests for:
 * ✅ OOMKilled detection
 * ✅ Evicted detection
 * ✅ ImagePullBackOff detection
 * ✅ CreateContainerConfigError detection
 * ✅ PendingTooLong detection with age check
 * ✅ CrashLoopBackOff with age check
 */
@DisplayName("PodStateDetector - Enhanced Detection Tests")
class PodStateDetectorTest {

    private PodStateDetector podStateDetector;

    @BeforeEach
    void setUp() {
        podStateDetector = new PodStateDetector();
    }

    // ==================== PART 1: OOMKILLED DETECTION ====================

    @Test
    @DisplayName("OOMKilled: Detects pod with OOMKilled reason")
    void testOOMKilledDetection() {
        PodInfo pod = createTestPod("default", "memory-hog", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Terminated");
        container.setReason("OOMKilled");
        container.setRestartCount(5);

        boolean isOOM = podStateDetector.isOOMKilled(pod);
        assertTrue(isOOM, "Should detect OOMKilled pod");
    }

    @Test
    @DisplayName("OOMKilled: Detects pod with OutOfMemory in reason")
    void testOOMKilledOutOfMemoryReason() {
        PodInfo pod = createTestPod("default", "oom-pod", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setReason("OutOfMemory");
        container.setState("Waiting");

        boolean isOOM = podStateDetector.isOOMKilled(pod);
        assertTrue(isOOM, "Should detect OutOfMemory pods");
    }

    @Test
    @DisplayName("OOMKilled: Does NOT detect healthy pod")
    void testHealthyPodNotOOMKilled() {
        PodInfo pod = createTestPod("default", "healthy", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Running");
        container.setReason("Started");
        container.setReady(true);

        boolean isOOM = podStateDetector.isOOMKilled(pod);
        assertFalse(isOOM, "Should not detect healthy pod as OOMKilled");
    }

    @Test
    @DisplayName("OOMKilled: Real-world scenario - memory-hog-oom from user")
    void testRealWorldMemoryHogOOM() {
        // Simulating: memory-hog-oom 0/1 OOMKilled 3 (29s ago)
        PodInfo pod = createTestPod("default", "memory-hog-oom", 1);
        pod.setStatus("Unknown"); // Failed/Unknown when OOMKilled
        ContainerInfo container = pod.getContainers().get(0);
        container.setName("app");
        container.setState("Terminated");
        container.setReason("OOMKilled");
        container.setRestartCount(3);
        container.setReady(false);

        assertTrue(podStateDetector.isOOMKilled(pod), "Should detect real OOMKilled scenario");
    }

    // ==================== PART 2: EVICTED DETECTION ====================

    @Test
    @DisplayName("Evicted: Detects pod with Evicted status")
    void testEvictedDetection() {
        PodInfo pod = createTestPod("default", "evicted-pod", 1);
        pod.setStatus("Evicted");

        boolean isEvicted = podStateDetector.isEvicted(pod);
        assertTrue(isEvicted, "Should detect Evicted pod");
    }

    @Test
    @DisplayName("Evicted: Detects pod with Evicted in reason")
    void testEvictedReason() {
        PodInfo pod = createTestPod("default", "resource-pressure", 1);
        pod.setStatus("Failed");
        pod.setReason("Evicted: node has insufficient memory");

        boolean isEvicted = podStateDetector.isEvicted(pod);
        assertTrue(isEvicted, "Should detect Evicted from reason");
    }

    @Test
    @DisplayName("Evicted: Does NOT detect running pod")
    void testRunningPodNotEvicted() {
        PodInfo pod = createTestPod("default", "running-pod", 1);
        pod.setStatus("Running");

        boolean isEvicted = podStateDetector.isEvicted(pod);
        assertFalse(isEvicted, "Should not detect running pod as evicted");
    }

    // ==================== PART 3: IMAGEPULLBACKOFF DETECTION ====================

    @Test
    @DisplayName("ImagePullBackOff: Detects ImagePullBackOff in reason")
    void testImagePullBackOffDetection() {
        PodInfo pod = createTestPod("default", "bad-image", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Waiting");
        container.setReason("ImagePullBackOff");

        boolean isImagePull = podStateDetector.isImagePullBackOff(pod);
        assertTrue(isImagePull, "Should detect ImagePullBackOff");
    }

    @Test
    @DisplayName("ImagePullBackOff: Detects ErrImagePull")
    void testErrImagePullDetection() {
        PodInfo pod = createTestPod("default", "bad-image-2", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Waiting");
        container.setReason("ErrImagePull: repository not found");

        boolean isImagePull = podStateDetector.isImagePullBackOff(pod);
        assertTrue(isImagePull, "Should detect ErrImagePull");
    }

    @Test
    @DisplayName("ImagePullBackOff: Does NOT detect running pod with image")
    void testRunningPodNotImagePullBackOff() {
        PodInfo pod = createTestPod("default", "running", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Running");
        container.setReason("Started");

        boolean isImagePull = podStateDetector.isImagePullBackOff(pod);
        assertFalse(isImagePull, "Should not detect running pod as ImagePullBackOff");
    }

    // ==================== PART 4: CREATE CONTAINER CONFIG ERROR ====================

    @Test
    @DisplayName("ConfigError: Detects CreateContainerConfigError")
    void testCreateContainerConfigErrorDetection() {
        PodInfo pod = createTestPod("default", "bad-config", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Waiting");
        container.setReason("CreateContainerConfigError");

        boolean isConfigError = podStateDetector.isCreateContainerConfigError(pod);
        assertTrue(isConfigError, "Should detect CreateContainerConfigError");
    }

    @Test
    @DisplayName("ConfigError: Detects InvalidImageName")
    void testInvalidImageNameDetection() {
        PodInfo pod = createTestPod("default", "invalid-image", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Waiting");
        container.setReason("InvalidImageName: image reference is invalid");

        boolean isConfigError = podStateDetector.isCreateContainerConfigError(pod);
        assertTrue(isConfigError, "Should detect InvalidImageName");
    }

    @Test
    @DisplayName("ConfigError: Detects InvalidMountPath")
    void testInvalidMountPathDetection() {
        PodInfo pod = createTestPod("default", "bad-mount", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Waiting");
        container.setReason("InvalidMountPath: /invalid path");

        boolean isConfigError = podStateDetector.isCreateContainerConfigError(pod);
        assertTrue(isConfigError, "Should detect InvalidMountPath");
    }

    // ==================== PART 5: PENDING TOO LONG DETECTION ====================

    @Test
    @DisplayName("Pending: Detects pod pending for 5+ minutes")
    void testPendingTooLongDetection() {
        PodInfo pod = createTestPod("default", "stuck-pending", 1);
        pod.setStatus("Pending");
        // Set creation time to 6 minutes ago
        pod.setCreationTimestamp(LocalDateTime.now().minusMinutes(6));
        
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Waiting");

        boolean isPending = podStateDetector.isPendingTooLong(pod);
        assertTrue(isPending, "Should detect pod pending for 5+ minutes");
    }

    @Test
    @DisplayName("Pending: Does NOT flag brand new pending pod")
    void testNewPendingPodNotFlagged() {
        PodInfo pod = createTestPod("default", "new-pending", 1);
        pod.setStatus("Pending");
        // Only 1 minute old
        pod.setCreationTimestamp(LocalDateTime.now().minusMinutes(1));
        
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Waiting");

        boolean isPending = podStateDetector.isPendingTooLong(pod);
        assertFalse(isPending, "Should NOT flag new pending pod");
    }

    @Test
    @DisplayName("Pending: Does NOT flag running pod")
    void testRunningPodNotPendingTooLong() {
        PodInfo pod = createTestPod("default", "running", 1);
        pod.setStatus("Running");

        boolean isPending = podStateDetector.isPendingTooLong(pod);
        assertFalse(isPending, "Should not detect running pod as pending");
    }

    // ==================== PART 6: CRASH LOOP WITH AGE CHECK ====================

    @Test
    @DisplayName("CrashLoop: Detects crash loop in old pod (2+ min)")
    void testCrashLoopAgeCheckOldPod() {
        PodInfo pod = createTestPod("default", "old-crashing", 1);
        pod.setCreationTimestamp(LocalDateTime.now().minusMinutes(3)); // 3 minutes old
        
        ContainerInfo container = pod.getContainers().get(0);
        container.setRestartCount(5);
        container.setState("Waiting");
        container.setReady(false);

        boolean isCrashLoop = podStateDetector.isCrashLoopBackOffWithAgeCheck(pod);
        assertTrue(isCrashLoop, "Should detect crash loop in old pod");
    }

    @Test
    @DisplayName("CrashLoop: Does NOT flag brand new crashing pod")
    void testCrashLoopAgeCheckNewPod() {
        PodInfo pod = createTestPod("default", "new-crashing", 1);
        pod.setCreationTimestamp(LocalDateTime.now().minusSeconds(30)); // Only 30 seconds old
        
        ContainerInfo container = pod.getContainers().get(0);
        container.setRestartCount(5);
        container.setState("Waiting");
        container.setReady(false);

        boolean isCrashLoop = podStateDetector.isCrashLoopBackOffWithAgeCheck(pod);
        assertFalse(isCrashLoop, "Should NOT flag brand new pod even if crashing");
    }

    @Test
    @DisplayName("CrashLoop: Healthy pod not flagged")
    void testHealthyPodNotCrashLoop() {
        PodInfo pod = createTestPod("default", "healthy", 1);
        pod.setCreationTimestamp(LocalDateTime.now().minusMinutes(10));
        
        ContainerInfo container = pod.getContainers().get(0);
        container.setRestartCount(0);
        container.setState("Running");
        container.setReady(true);

        boolean isCrashLoop = podStateDetector.isCrashLoopBackOffWithAgeCheck(pod);
        assertFalse(isCrashLoop, "Should not flag healthy pod");
    }

    // ==================== PART 7: COMBINED DIAGNOSTICS ====================

    @Test
    @DisplayName("Diagnosis: OOMKilled pod shows correct diagnosis")
    void testDiagnosisOOMKilled() {
        PodInfo pod = createTestPod("default", "memory-hog", 1);
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Terminated");
        container.setReason("OOMKilled");

        String diagnosis = podStateDetector.diagnoseUnhealthyPod(pod);
        assertTrue(diagnosis.contains("OOMKilled"), "Diagnosis should mention OOMKilled");
    }

    @Test
    @DisplayName("Diagnosis: Multiple issues detected")
    void testDiagnosisMultipleIssues() {
        PodInfo pod = createTestPod("default", "broken", 1);
        pod.setStatus("Evicted");
        
        String diagnosis = podStateDetector.diagnoseUnhealthyPod(pod);
        assertTrue(diagnosis.contains("Evicted"), "Should detect evicted status");
    }

    @Test
    @DisplayName("Diagnosis: Healthy pod returns no issues")
    void testDiagnosisHealthyPod() {
        PodInfo pod = createTestPod("default", "healthy", 1);
        pod.setStatus("Running");
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Running");
        container.setReady(true);
        container.setRestartCount(0);

        String diagnosis = podStateDetector.diagnoseUnhealthyPod(pod);
        assertEquals("No issues detected", diagnosis);
    }

    // ==================== PART 8: REAL-WORLD SCENARIOS ====================

    @Test
    @DisplayName("Scenario: User's memory-hog-oom detection flow")
    void testUserMemoryHogScenario() {
        // Simulating the actual pod output from user:
        // memory-hog-oom 0/1 OOMKilled 0 30s
        // memory-hog-oom 0/1 OOMKilled 1 (2s ago) 32s
        // memory-hog-oom 0/1 CrashLoopBackOff 1 (1s ago) 33s

        PodInfo pod = createTestPod("default", "memory-hog-oom", 1);
        pod.setStatus("Unknown");
        pod.setCreationTimestamp(LocalDateTime.now().minusSeconds(33));
        
        ContainerInfo container = pod.getContainers().get(0);
        container.setName("app");
        container.setState("Terminated");
        container.setReason("OOMKilled");
        container.setRestartCount(3);
        container.setReady(false);

        // Detection results
        boolean isOOM = podStateDetector.isOOMKilled(pod);
        boolean isCrashLoop = podStateDetector.isCrashLoopBackOffWithAgeCheck(pod);
        
        // Should detect OOMKilled
        assertTrue(isOOM, "Should detect OOMKilled");
        
        // Should NOT detect crash loop (pod too new, only 33 seconds)
        assertFalse(isCrashLoop, "Should not flag as crash loop - pod too new");
        
        String diagnosis = podStateDetector.diagnoseUnhealthyPod(pod);
        assertTrue(diagnosis.contains("OOMKilled"), "Diagnosis should prioritize OOMKilled");
    }

    @Test
    @DisplayName("Scenario: ImagePullBackOff with old pod")
    void testImagePullBackOffOldPod() {
        PodInfo pod = createTestPod("default", "bad-registry", 1);
        pod.setStatus("Pending");
        pod.setCreationTimestamp(LocalDateTime.now().minusMinutes(10));
        
        ContainerInfo container = pod.getContainers().get(0);
        container.setState("Waiting");
        container.setReason("ImagePullBackOff");

        boolean isImagePull = podStateDetector.isImagePullBackOff(pod);
        assertTrue(isImagePull, "Should detect ImagePullBackOff on old pod");
    }

    @Test
    @DisplayName("Scenario: Evicted pod on node resource pressure")
    void testEvictedNodeResourcePressure() {
        PodInfo pod = createTestPod("default", "evicted-app", 0); // 0 containers = Failed
        pod.setStatus("Failed");
        pod.setReason("Evicted: node has insufficient memory");
        pod.setCreationTimestamp(LocalDateTime.now().minusMinutes(5));

        boolean isEvicted = podStateDetector.isEvicted(pod);
        assertTrue(isEvicted, "Should detect evicted pod from resource pressure");
    }

    // ==================== HELPER METHODS ====================

    private PodInfo createTestPod(String namespace, String name, int numContainers) {
        PodInfo pod = new PodInfo();
        pod.setNamespace(namespace);
        pod.setName(name);
        pod.setStatus("Running");
        pod.setCreationTimestamp(LocalDateTime.now());

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