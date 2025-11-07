package com.example.demo.controller;

import com.example.demo.model.DeploymentInfo;
import com.example.demo.model.ErrorResponse;
import com.example.demo.model.IngressInfo;
import com.example.demo.model.PodInfo;
import com.example.demo.model.ServiceInfo;
import com.example.demo.service.KubernetesService;
import io.kubernetes.client.openapi.ApiException;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
//opentelemtery related imports 
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import io.opentelemetry.api.trace.Span;
// Add this import
import io.opentelemetry.api.common.AttributeKey;
// Add this import for Redis Caching
import com.example.demo.service.CacheService;


@RestController
@RequestMapping("/api/kubernetes")
@CrossOrigin(origins = "http://localhost:4200")
public class KubernetesController {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesController.class);
   
    @Autowired
    private KubernetesService kubernetesService;
    @Value("${kubernetes.namespace:default}")
    private String namespace;
    
     // Add this helper method to your controller class
    private void logTraceHeaders(HttpServletRequest request) {
        String traceId = request.getHeader("X-Frontend-Trace-Id");
        String component = request.getHeader("X-Frontend-Component");
        String method = request.getHeader("X-Frontend-Method");
        String timestamp = request.getHeader("X-Frontend-Timestamp");

        if (traceId != null) {
            // Add to OpenTelemetry span
            Span currentSpan = Span.current();
            currentSpan.setAttribute("frontend.trace.id", traceId);
            currentSpan.setAttribute("frontend.component", component);
            currentSpan.setAttribute("frontend.method", method);
            currentSpan.setAttribute("frontend.timestamp", timestamp);

            logger.info("🔍 Auto-trace: {} from {} [{}]", method, component, traceId);
        }
    }


    @GetMapping("/")
    public String home(){
        return "monitoring backend is alive - cluster wide monitoring enabled";
    }

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

    // Add this field to your controller
    @Autowired
    private CacheService cacheService;

    // Update ONLY your getPods method:
@GetMapping("/pods")
public ResponseEntity<?> getPods(HttpServletRequest request) {
    logTraceHeaders(request);
    String frontendTraceId = request.getHeader("X-Frontend-Trace-Id");
    String component = request.getHeader("X-Frontend-Component");
    String timestamp = request.getHeader("X-Frontend-Timestamp");
    
    try {
        // ✅ CHECK CACHE FIRST!
        List<PodInfo> pods = cacheService.getCachedPodData();
        
        if (pods == null) {
            // Cache miss - fetch from Kubernetes
            logger.info("Cache miss - fetching pods from Kubernetes");
            pods = kubernetesService.getPodInfoClusterWide();
            logger.debug("Successfully fetched {} pods from Kubernetes", pods.size());
            
            // Cache for next time
            cacheService.cachePodData((List<PodInfo>)(List<?>) pods);
        } else {
            logger.info("Cache hit - returning {} cached pods", pods.size());
        }
        
        // Store request metadata if provided
        if (frontendTraceId != null) {
            cacheService.storeRequestMetadata(frontendTraceId, component, timestamp);
        }
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "pods", pods,
            "redis", cacheService.isRedisHealthy() ? "connected" : "unavailable"
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
    @GetMapping("/namespaces")
public ResponseEntity<Map<String, Object>> getNamespaces() {
    try {
        List<String> namespaces = kubernetesService.getNamespaces();
        return ResponseEntity.ok(Map.of(
            "success", true,
            "namespaces", namespaces
        ));
    } catch (Exception e) {
        logger.error("Error fetching namespaces: {}", e.getMessage(),e);
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", e.getMessage()!=null ? e.getMessage() : "Unknown error"
            ));
    }
}
@GetMapping("/namespaces/{namespace}/pods/{podName}/logs")
public ResponseEntity<String> getPodLogs(
        @PathVariable String namespace,
        @PathVariable String podName,
        @RequestParam(required = false) String container,
        @RequestParam(required = false, defaultValue = "100") int tailLines) {
    return kubernetesService.getPodLogs(namespace, podName, container, tailLines);
}

@GetMapping("/namespaces/{namespace}/pods/{podName}")
public ResponseEntity<Object> getPodDetails(
        @PathVariable String namespace,
        @PathVariable String podName) {
    return kubernetesService.getPodDetails(namespace, podName);
}



//for deployments , services, and ingress
 @GetMapping("/deployments")
    public ResponseEntity<?> getDeployments(@RequestParam(required = false) String namespace) {
        try {
            List<DeploymentInfo> deployments = kubernetesService.getAllDeployments(namespace);
            return ResponseEntity.ok(Map.of("success", true, "deployments", deployments));
        } catch (ApiException e) {
            return ResponseEntity.status(e.getCode())
                .body(Map.of("success", false, "error", e.getResponseBody()));
        }
    }

    @GetMapping("/services")
    public ResponseEntity<?> getServices(@RequestParam(required = false) String namespace) {
        try {
            List<ServiceInfo> services = kubernetesService.getServices(namespace);
            return ResponseEntity.ok(Map.of("success", true, "services", services));
        } catch (ApiException e) {
            return ResponseEntity.status(e.getCode())
                .body(Map.of("success", false, "error", e.getResponseBody()));
        }
    }

    @GetMapping("/ingresses")
    public ResponseEntity<?> getIngresses(@RequestParam(required = false) String namespace) {
        try {
            List<IngressInfo> ingresses = kubernetesService.getIngresses(namespace);
            return ResponseEntity.ok(Map.of("success", true, "ingresses", ingresses));
        } catch (ApiException e) {
            return ResponseEntity.status(e.getCode())
                .body(Map.of("success", false, "error", e.getResponseBody()));
        }
    }

    // Add this endpoint to your controller for debugging Redis

@GetMapping("/redis/test")
public ResponseEntity<?> testRedis() {
    Map<String, Object> response = new HashMap<>();
    
    try {
        // Test basic Redis operations
        boolean isHealthy = cacheService.isRedisHealthy();
        response.put("redis_healthy", isHealthy);
        response.put("redis_status", cacheService.getRedisStatus());
        
        if (isHealthy) {
            // Test some operations
            response.put("test_results", Map.of(
                "basic_connection", "✓ Connected",
                "write_test", "✓ Can write",
                "read_test", "✓ Can read"
            ));
        } else {
            response.put("test_results", Map.of(
                "basic_connection", "✗ Failed",
                "error", "Cannot connect to Redis"
            ));
        }
        
        // Environment info
        response.put("environment", Map.of(
            "REDIS_HOST", System.getenv("REDIS_HOST"),
            "REDIS_PORT", System.getenv("REDIS_PORT")
        ));
        
        return ResponseEntity.ok(response);
        
    } catch (Exception e) {
        logger.error("Redis test failed", e);
        response.put("error", e.getMessage());
        response.put("redis_healthy", false);
        return ResponseEntity.status(500).body(response);
    }
}

}