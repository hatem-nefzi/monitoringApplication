package com.example.demo.model;

import lombok.Data;

@Data
public class ContainerInfo {
    private String name;
    private String image;

    public ContainerInfo(String name, String image) {
        this.name = name;
        this.image = image;
    }
}