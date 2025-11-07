package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import com.example.demo.model.PodInfo;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.example.demo.model.AutoRemediation.RemediationAction;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
     @Autowired
    private ObjectMapper objectMapper; // ← Inject instead of creating new
    
    private boolean redisAvailable = false;
    
    @PostConstruct
    public void init() {
        logger.info("Initializing CacheService...");
        if (redisTemplate == null) {
            logger.warn("RedisTemplate is null - Redis functionality will be disabled");
            return;
        }
        
        try {
            // Test Redis connection during startup
            redisTemplate.opsForValue().set("startup-test", "ok", Duration.ofSeconds(10));
            String result = (String) redisTemplate.opsForValue().get("startup-test");
            redisAvailable = "ok".equals(result);
            
            if (redisAvailable) {
                logger.info("Redis connection successful - caching enabled");
                redisTemplate.delete("startup-test");
            } else {
                logger.warn("Redis connection test failed - result was: {}", result);
            }
        } catch (Exception e) {
            logger.error("Redis connection failed during startup: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            redisAvailable = false;
        }
    }

    public List<PodInfo> cachePodData(List<PodInfo> pods) {
        if (redisTemplate == null) {
            logger.debug("Redis template not available - skipping cache");
            return pods;
        }
        
        if (!redisAvailable) {
            logger.debug("Redis marked as unavailable - skipping cache");
            return pods;
        }
        
        try {
            logger.info("Attempting to cache pod data to Redis - {} pods", pods.size());
            redisTemplate.opsForValue().set("pods:all", pods, Duration.ofMinutes(5));
            logger.info("Successfully cached pod data to Redis");
            return pods;
        } catch (RedisConnectionFailureException e) {
            logger.error("Redis connection failure while caching: {}", e.getMessage());
            redisAvailable = false; // Mark as unavailable for future calls
            return pods;
        } catch (Exception e) {
            logger.warn("Redis caching failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return pods;
        }
    }
    
    public void storeRequestMetadata(String traceId, String component, String timestamp) {
        if (redisTemplate == null || !redisAvailable) {
            logger.debug("Redis not available for metadata storage");
            return;
        }
        
        try {
            String key = "trace:" + traceId;
            logger.debug("Storing trace metadata in Redis: {}", key);
            
            redisTemplate.opsForHash().put(key, "component", component);
            redisTemplate.opsForHash().put(key, "timestamp", timestamp);
            redisTemplate.opsForHash().put(key, "status", "processed");
            redisTemplate.expire(key, Duration.ofHours(1));
            
            logger.debug("Successfully stored metadata for trace: {}", traceId);
        } catch (RedisConnectionFailureException e) {
            logger.error("Redis connection failure while storing metadata: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            logger.warn("Redis metadata storage failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
    
    public boolean isRedisHealthy() {
        if (redisTemplate == null) {
            logger.debug("RedisTemplate is null");
            return false;
        }
        
        try {
            redisTemplate.opsForValue().set("health", "ok", Duration.ofSeconds(10));
            String result = (String) redisTemplate.opsForValue().get("health");
            boolean healthy = "ok".equals(result);
            
            if (healthy) {
                redisTemplate.delete("health");
                redisAvailable = true; // Update availability status
                logger.debug("Redis health check passed");
            } else {
                logger.warn("Redis health check failed - expected 'ok', got: {}", result);
                redisAvailable = false;
            }
            
            return healthy;
        } catch (RedisConnectionFailureException e) {
            logger.error("Redis connection failure during health check: {}", e.getMessage());
            redisAvailable = false;
            return false;
        } catch (Exception e) {
            logger.warn("Redis health check failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            redisAvailable = false;
            return false;
        }
    }
    
    // Helper method to get Redis status for debugging
    public String getRedisStatus() {
        if (redisTemplate == null) {
            return "RedisTemplate is null";
        }
        return redisAvailable ? "Available" : "Unavailable";
    }

    public List<PodInfo> getCachedPodData() {
    if (redisTemplate == null || !redisAvailable) {
        logger.info("Redis not available - returning null for cache lookup");
        return null;
    }
    
    try {
        logger.info("Attempting to retrieve cached pod data from Redis");
        
        Object cached = redisTemplate.opsForValue().get("pods:all");
        
        if (cached == null) {
            logger.info("Cache miss - no data found for key 'pods:all'");
            return null;
        }
        
        logger.info("Cache hit - retrieved data of type: {}", cached.getClass().getName());
        
        // Cast directly - your RedisConfig with type info should handle this
        @SuppressWarnings("unchecked")
        List<PodInfo> pods = (List<PodInfo>) cached;
        
        logger.info("Successfully deserialized {} pods from cache", pods.size());
        return pods;
        
    } catch (ClassCastException e) {
        logger.error("❌ Type mismatch in cache - clearing corrupt data: {}", e.getMessage());
        try {
            redisTemplate.delete("pods:all");
            logger.info("Cleared corrupt cache");
        } catch (Exception ex) {
            logger.error("Failed to clear cache: {}", ex.getMessage());
        }
        return null;
    } catch (Exception e) {
        logger.error("Error retrieving cached pod data: {}", e.getMessage(), e);
        return null;
    } 
}
    /**
 * Store remediation action in Redis
 * Key format: remediation:action:{id}
 * Also maintains a sorted set for chronological retrieval
 */
public void storeRemediationAction(RemediationAction action) {
        if (!isRedisHealthy()) {
            logger.warn("Redis unavailable - cannot store remediation action");
            return;
        }

        try {
            String key = "remediation:action:" + action.getId();
            // Use the injected ObjectMapper instead of creating new one
            String jsonValue = objectMapper.writeValueAsString(action); // ← Fixed!

            // Store the action
            redisTemplate.opsForValue().set(key, jsonValue, Duration.ofDays(7));

            // Add to sorted set for chronological queries
            String sortedSetKey = "remediation:actions:timeline";
            redisTemplate.opsForZSet().add(
                sortedSetKey, 
                action.getId(), 
                System.currentTimeMillis()
            );

            logger.debug("Stored remediation action in Redis: {}", action.getId());
        } catch (Exception e) {
            logger.error("Failed to store remediation action in Redis", e);
        }
    }
}
    



