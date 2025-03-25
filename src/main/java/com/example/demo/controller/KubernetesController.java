package com.example.demo.controller;

import com.example.demo.service.KubernetesService;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesController {

    @Autowired
    private KubernetesService kubernetesService;

    @GetMapping("/pod-names")
public Map<String, Object> getPodNames() {
    try {
        return Map.of(
            "success", true,
            "podNames", kubernetesService.getPodNames()
        );
    } catch (Exception e) {
        return Map.of(
            "success", false,
            "error", e.getMessage()
        );
    }
}
@GetMapping("/pods")
public Map<String, Object> getPods() {
    try {
        return Map.of(
            "success", true,
            "pods", kubernetesService.getPodInfo(null)
        );
    } catch (Exception e) {
        return Map.of(
            "success", false,
            "error", e.getMessage()
        );
    }
}
}