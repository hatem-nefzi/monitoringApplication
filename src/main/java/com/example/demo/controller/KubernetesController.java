package com.example.demo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.PodInfo;
import com.example.demo.service.KubernetesService;

@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesController {
    private final KubernetesService kubernetesService;

    public KubernetesController(KubernetesService kubernetesService) {
        this.kubernetesService = kubernetesService;
    }

    @GetMapping("/pods")
    public List<PodInfo> getPods() {
        return kubernetesService.getPods();
    }

    @GetMapping("/deployments")
    public List<DeploymentInfo> getDeployments() {
        return kubernetesService.getDeployments();
    }
}
