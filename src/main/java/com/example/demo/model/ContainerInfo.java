package com.example.demo.model;

import lombok.Data;

@Data
public class ContainerInfo {
    private String name;
    private String image;
    private boolean ready;
    private int restartCount;
    private String state; // "Running", "Waiting", "Terminated", "Unknown"

    public ContainerInfo(String name, String image, boolean ready, int restartCount, String state) {
        this.name = name;
        this.image = image;
        this.ready = ready;
        this.restartCount = restartCount;
        this.state = state;
    }
}