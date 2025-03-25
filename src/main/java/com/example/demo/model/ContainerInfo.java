package com.example.demo.model;

import com.example.demo.serializer.IntOrStringSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import lombok.Data;

import java.util.List;

@Data
public class ContainerInfo {
    private String name;
    private String image;
    private boolean ready;
    
    @JsonSerialize(using = IntOrStringSerializer.class)
    private Integer port;

    public ContainerInfo(V1Container container) {
        this.name = container.getName();
        this.image = container.getImage();
        this.ready = false;
        
        if (container.getPorts() != null && !container.getPorts().isEmpty()) {
            V1ContainerPort containerPort = container.getPorts().get(0);
            this.port = containerPort.getContainerPort();
        }
    }
}