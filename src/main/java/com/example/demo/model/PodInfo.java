package com.example.demo.model;

import lombok.Data;

import java.util.List;

@Data
public class PodInfo {
    private String name;
    private String namespace;
    private String status;
    private String nodeName;
    private String nodeIP;
    private List<ContainerInfo> containers;

    public PodInfo(String name, String namespace, String status, 
                 String nodeName, String nodeIP, List<ContainerInfo> containers) {
        this.name = name;
        this.namespace = namespace;
        this.status = status;
        this.nodeName = nodeName;
        this.nodeIP = nodeIP;
        this.containers = containers;
    }
}