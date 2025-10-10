package com.example.demo.model.AutoRemediation;

/**
 * Defines WHEN and HOW the system should auto-remediate
 * This is configurable - you can enable/disable rules
 */
public class RemediationPolicy {
    
    private boolean enabled;                  // Master switch
    private boolean autoRestartCrashingPods;  // Restart pods in CrashLoopBackOff
    private int maxRestartAttempts;           // Don't restart forever
    private boolean autoDeleteFailedPods;     // Clean up Failed pods
    private boolean autoScaleOnHighCPU;       // Trigger HPA-like scaling
    private double cpuThresholdPercent;       // What's "high CPU"? (e.g., 80%)
    private boolean notifyOnAction;           // Send alerts when we act
    
    // Constructor with safe defaults
    public RemediationPolicy() {
        this.enabled = true;
        this.autoRestartCrashingPods = true;
        this.maxRestartAttempts = 3;
        this.autoDeleteFailedPods = false; // Be cautious by default
        this.autoScaleOnHighCPU = false;   // Let HPA handle this
        this.cpuThresholdPercent = 80.0;
        this.notifyOnAction = true;
    }

    // Getters and Setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoRestartCrashingPods() { return autoRestartCrashingPods; }
    public void setAutoRestartCrashingPods(boolean autoRestartCrashingPods) { 
        this.autoRestartCrashingPods = autoRestartCrashingPods; 
    }

    public int getMaxRestartAttempts() { return maxRestartAttempts; }
    public void setMaxRestartAttempts(int maxRestartAttempts) { 
        this.maxRestartAttempts = maxRestartAttempts; 
    }

    public boolean isAutoDeleteFailedPods() { return autoDeleteFailedPods; }
    public void setAutoDeleteFailedPods(boolean autoDeleteFailedPods) { 
        this.autoDeleteFailedPods = autoDeleteFailedPods; 
    }

    public boolean isAutoScaleOnHighCPU() { return autoScaleOnHighCPU; }
    public void setAutoScaleOnHighCPU(boolean autoScaleOnHighCPU) { 
        this.autoScaleOnHighCPU = autoScaleOnHighCPU; 
    }

    public double getCpuThresholdPercent() { return cpuThresholdPercent; }
    public void setCpuThresholdPercent(double cpuThresholdPercent) { 
        this.cpuThresholdPercent = cpuThresholdPercent; 
    }

    public boolean isNotifyOnAction() { return notifyOnAction; }
    public void setNotifyOnAction(boolean notifyOnAction) { 
        this.notifyOnAction = notifyOnAction; 
    }
}