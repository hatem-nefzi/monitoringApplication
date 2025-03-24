package com.example.demo.model;

public class DeploymentInfo {
    private String name;
    private Integer replicas;

    public DeploymentInfo(String name, Integer replicas) {
        this.name = name;
        this.replicas = replicas;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }
}
