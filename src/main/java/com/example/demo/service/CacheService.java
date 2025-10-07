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
import java.util.List;

@Service
public class CacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
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
    
    public List<Object> cachePodData(List<Object> pods) {
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
        
        if (cached instanceof List) {
            List<PodInfo> pods = (List<PodInfo>) cached;
            logger.info("Successfully deserialized {} pods from cache", pods.size());
            return pods;
        } else {
            logger.warn("Cached data is not a List, it's a: {}", cached.getClass());
            return null;
        }
        
    } catch (Exception e) {
        logger.error("Error retrieving cached pod data: {}", e.getMessage(), e);
        return null;
    }
}
}