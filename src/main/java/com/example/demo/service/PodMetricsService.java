package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

@Service
public class PodMetricsService {
    private static final Logger logger = LoggerFactory.getLogger(PodMetricsService.class);

    public Map<String, String> getPodMetrics(String namespace, String podName) {
        Map<String, String> metrics = new HashMap<>();
        
        try {
            // Execute kubectl top command
            Process process = Runtime.getRuntime().exec(
                new String[] {
                    "kubectl", "top", "pod", podName,
                    "--namespace", namespace,
                    "--no-headers"
                });
            
            // Read the output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                String line = reader.readLine();
                if (line != null) {
                    // Sample output: "pod-name 100m 50Mi"
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        metrics.put("cpu", parts[1]);
                        metrics.put("memory", parts[2]);
                    }
                }
            }
            
            // Check for errors
            if (process.waitFor() != 0) {
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String error = errorReader.lines().collect(Collectors.joining("\n"));
                    metrics.put("error", error);
                    logger.error("Metrics error: {}", error);
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to get metrics for pod {}/{}: {}", namespace, podName, e.getMessage());
            metrics.put("error", "Metrics collection failed: " + e.getMessage());
        }
        
        return metrics;
    }
}