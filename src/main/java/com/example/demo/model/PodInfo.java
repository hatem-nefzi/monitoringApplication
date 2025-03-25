package com.example.demo.model;

import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class PodInfo {
    private String name;
    private String namespace;
    private String status;
    private List<ContainerInfo> containers;

    public PodInfo(String name, String namespace, String status, List<ContainerInfo> containers) {
        this.name = name;
        this.namespace = namespace;
        this.status = status;
        this.containers = containers;
    }
}