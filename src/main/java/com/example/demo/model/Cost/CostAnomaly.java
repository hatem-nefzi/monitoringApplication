package com.example.demo.model.Cost;

import java.time.LocalDateTime;

/**
 * ⚠️ Cost Anomaly Model
 * Represents detected unusual cost patterns
 */
public class CostAnomaly {
    private String namespace;
    private String type; // "SPIKE", "DROP", "UNUSUAL_PATTERN"
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    private double baselineCost;
    private double currentCost;
    private double changePercent;
    private String culprit; // Pod/deployment causing anomaly
    private String message;
    private LocalDateTime detectedAt;
    private LocalDateTime anomalyStarted;
    private boolean resolved;

    // Constructors
    public CostAnomaly() {
        this.detectedAt = LocalDateTime.now();
        this.resolved = false;
    }

    public CostAnomaly(String namespace, String type, String severity, 
                       double baselineCost, double currentCost, String culprit) {
        this.namespace = namespace;
        this.type = type;
        this.severity = severity;
        this.baselineCost = baselineCost;
        this.currentCost = currentCost;
        this.culprit = culprit;
        this.detectedAt = LocalDateTime.now();
        this.resolved = false;
        this.changePercent = baselineCost > 0 ? 
            ((currentCost - baselineCost) / baselineCost) * 100 : 0;
    }

    // Getters and Setters
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public double getBaselineCost() {
        return baselineCost;
    }

    public void setBaselineCost(double baselineCost) {
        this.baselineCost = baselineCost;
    }

    public double getCurrentCost() {
        return currentCost;
    }

    public void setCurrentCost(double currentCost) {
        this.currentCost = currentCost;
    }

    public double getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(double changePercent) {
        this.changePercent = changePercent;
    }

    public String getCulprit() {
        return culprit;
    }

    public void setCulprit(String culprit) {
        this.culprit = culprit;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public LocalDateTime getAnomalyStarted() {
        return anomalyStarted;
    }

    public void setAnomalyStarted(LocalDateTime anomalyStarted) {
        this.anomalyStarted = anomalyStarted;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}