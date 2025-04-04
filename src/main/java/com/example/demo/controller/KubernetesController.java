package com.example.demo.controller;

import com.example.demo.model.ErrorResponse;
import com.example.demo.model.PodInfo;
import com.example.demo.service.KubernetesService;
import io.kubernetes.client.openapi.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kubernetes")
@CrossOrigin(origins = "http://localhost:4200")
public class KubernetesController {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesController.class);

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
            logger.error("Error fetching pod names", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    @GetMapping("/pods")
    public ResponseEntity<?> getPods() {
        try {
            List<PodInfo> pods = kubernetesService.getPodInfo(null);
            logger.debug("Successfully fetched {} pods", pods.size());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "pods", pods
            ));
        } catch (ApiException e) {
            logger.error("API error fetching pods: {}", e.getResponseBody(), e);
            return ResponseEntity.status(e.getCode())
                .body(Map.of(
                    "success", false,
                    "error", "Kubernetes API error",
                    "details", e.getResponseBody()
                ));
        }
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        logger.error("Kubernetes API Exception: {}", e.getResponseBody(), e);
        return ResponseEntity.status(e.getCode())
            .body(new ErrorResponse(
                "Kubernetes API Error: " + e.getMessage(),
                e.getCode()
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        logger.error("Unexpected error", e);
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse(
                "Internal Error: " + e.getMessage(),
                500
            ));
    }
    @GetMapping("/namespaces")
public ResponseEntity<Map<String, Object>> getNamespaces() {
    try {
        List<String> namespaces = kubernetesService.getNamespaces();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "namespaces", namespaces
        ));
    } catch (Exception e) {
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
    }
}
}