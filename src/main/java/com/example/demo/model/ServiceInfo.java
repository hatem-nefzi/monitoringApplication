package com.example.demo.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ServiceInfo {
    private String name;
    private String namespace;
    private String type;
    private String clusterIP;
    private Map<String, String> labels;
    private String creationTimestamp;
    private List<ServicePort> ports;
    
    // No need to define the constructor unless you want a custom one.
}
