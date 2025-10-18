package com.example.demo.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ContainerInfo {
    private String name;
    private String image;
    private boolean ready;
    private int restartCount;
    private String state; // "Running", "Waiting", "Terminated", "Unknown"
      // ✅ ADD THIS
    private String reason;
    private LocalDateTime lastStateChangeTime;
    public ContainerInfo() {
    }
    public ContainerInfo(String name, String image, boolean ready, int restartCount, String state) {
        this.name = name;
        this.image = image;
        this.ready = ready;
        this.restartCount = restartCount;
        this.state = state;
        
    }
    // Add methods
public String getReason() {
    return reason;
}

public void setReason(String reason) {
    this.reason = reason;
}

public LocalDateTime getLastStateChangeTime() {
    return lastStateChangeTime;
}

public void setLastStateChangeTime(LocalDateTime lastStateChangeTime) {
    this.lastStateChangeTime = lastStateChangeTime;
}
}


