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

    public ServiceInfo() {}

    // You can also create a constructor that takes V1Service if needed
}

@Data
class ServicePort {
    private String name;
    private String protocol;
    private Integer port;
    private String targetPort;
    private Integer nodePort;
}