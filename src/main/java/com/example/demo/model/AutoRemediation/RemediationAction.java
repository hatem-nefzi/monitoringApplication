package com.example.demo.model.AutoRemediation;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a single auto-remediation action taken by the system
 * This is what gets logged and displayed in the UI
 */
public class RemediationAction {
    
    private String id;                    // Unique identifier
    private String podName;               // Which pod was affected
    private String namespace;             // Which namespace
    private String issue;                 // What was the problem? (e.g., "CrashLoopBackOff")
    private String action;                // What did we do? (e.g., "Pod Restart")
    private RemediationStatus status;     // Success/Failed/InProgress
    private String timestamp;      // When did this happen
    private String reason;                // Why did we take this action
    private Map<String, String> metadata; // Extra info (CPU usage, restart count, etc)
    private String error;                 // If it failed, why?

    public enum RemediationStatus {
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        SKIPPED
    }

    // Constructors
    public RemediationAction() {
        this.timestamp = LocalDateTime.now().toString();
        this.status = RemediationStatus.IN_PROGRESS;
    }

    public RemediationAction(String podName, String namespace, String issue, String action, String reason) {
        this();
        this.id = generateId(podName, namespace);
        this.podName = podName;
        this.namespace = namespace;
        this.issue = issue;
        this.action = action;
        this.reason = reason;
    }

    private String generateId(String podName, String namespace) {
        return String.format("%s-%s-%d", namespace, podName, System.currentTimeMillis());
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPodName() { return podName; }
    public void setPodName(String podName) { this.podName = podName; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public RemediationStatus getStatus() { return status; }
    public void setStatus(RemediationStatus status) { this.status = status; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    @Override
    public String toString() {
        return String.format("RemediationAction{pod=%s/%s, issue=%s, action=%s, status=%s}", 
            namespace, podName, issue, action, status);
    }
}