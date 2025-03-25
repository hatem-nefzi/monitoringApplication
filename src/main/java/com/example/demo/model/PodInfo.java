package com.example.demo.model;

import lombok.Data;

@Data
public class PodInfo {
    private String name;
    private String namespace;
    private String status;

    public PodInfo(String name, String namespace, String status) {
        this.name = name;
        this.namespace = namespace;
        this.status = status;
    }
}