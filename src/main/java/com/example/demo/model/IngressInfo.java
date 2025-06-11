package com.example.demo.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class IngressInfo {
    private String name;
    private String namespace;
    private Map<String, String> annotations;
    private String creationTimestamp;
    private List<String> hosts;
    private List<String> paths;
    
    // No need for an explicit constructor unless you're doing something custom
}
