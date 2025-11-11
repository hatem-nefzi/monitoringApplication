package com.example.demo.service;

import com.example.demo.model.PodInfo;
import com.example.demo.model.AutoRemediation.RemediationAction;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

@Service
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private boolean redisAvailable = false;

    /**
     * Initializes Redis connection and performs a simple write/read test
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing CacheService...");

        if (redisTemplate == null) {
            logger.warn("RedisTemplate is null - Redis functionality will be disabled");
            return;
        }

        try {
            redisTemplate.opsForValue().set("startup-test", "ok", Duration.ofSeconds(10));
            String result = (String) redisTemplate.opsForValue().get("startup-test");
            redisAvailable = "ok".equals(result);

            if (redisAvailable) {
                logger.info("✅ Redis connection successful - caching enabled");
                redisTemplate.delete("startup-test");
            } else {
                logger.warn("Redis connection test failed - result was: {}", result);
            }
        } catch (Exception e) {
            logger.error("❌ Redis connection failed during startup: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            redisAvailable = false;
        }
    }

    /**
     * Caches the list of pods as JSON in Redis (valid 5 minutes)
     */
    public List<PodInfo> cachePodData(List<PodInfo> pods) {
        if (!redisAvailable || pods == null || pods.isEmpty()) return pods;

        try {
            String json = objectMapper.writeValueAsString(pods);
            redisTemplate.opsForValue().set("pods:all", json, Duration.ofMinutes(5));
            logger.info("✅ Cached {} pods to Redis as JSON", pods.size());
        } catch (Exception e) {
            logger.error("❌ Redis caching failed: {}", e.getMessage());
        }
        return pods;
    }

    /**
     * Retrieves cached pod data and deserializes it into List<PodInfo>
     */
    @SuppressWarnings("unchecked")
    public List<PodInfo> getCachedPodData() {
        if (!redisAvailable) return null;

        try {
            Object value = redisTemplate.opsForValue().get("pods:all");
            if (value == null) return null;

            String json = value.toString();
            List<PodInfo> pods = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PodInfo.class)
            );
            logger.info("✅ Returning pod data from Redis cache ({} pods)", pods.size());
            return pods;
        } catch (Exception e) {
            logger.error("❌ Failed to deserialize cached pod data: {}", e.getMessage());
            redisTemplate.delete("pods:all");
            return null;
        }
    }

    /**
     * Stores request metadata (used for tracing)
     */
    public void storeRequestMetadata(String traceId, String component, String timestamp) {
        if (!redisAvailable) {
            logger.debug("Redis unavailable for metadata storage");
            return;
        }

        try {
            String key = "trace:" + traceId;
            redisTemplate.opsForHash().put(key, "component", component);
            redisTemplate.opsForHash().put(key, "timestamp", timestamp);
            redisTemplate.opsForHash().put(key, "status", "processed");
            redisTemplate.expire(key, Duration.ofHours(1));
            logger.debug("Stored metadata for trace: {}", traceId);
        } catch (RedisConnectionFailureException e) {
            logger.error("Redis connection failure while storing metadata: {}", e.getMessage());
            redisAvailable = false;
        } catch (Exception e) {
            logger.warn("Redis metadata storage failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * Stores a remediation action as JSON for 7 days
     */
    public void storeRemediationAction(RemediationAction action) {
        if (action == null || !redisAvailable) {
            logger.warn("Redis unavailable or invalid action - cannot store remediation action");
            return;
        }

        try {
            String key = "remediation:action:" + action.getId();
            String jsonValue = objectMapper.writeValueAsString(action);
            redisTemplate.opsForValue().set(key, jsonValue, Duration.ofDays(7));

            String sortedSetKey = "remediation:actions:timeline";
            redisTemplate.opsForZSet().add(sortedSetKey, action.getId(), System.currentTimeMillis());
            logger.debug("Stored remediation action in Redis: {}", action.getId());
        } catch (Exception e) {
            logger.error("Failed to store remediation action in Redis: {}", e.getMessage());
        }
    }

    /**
     * Periodic health check for Redis availability
     */
    public boolean isRedisHealthy() {
        if (redisTemplate == null) return false;

        try {
            redisTemplate.opsForValue().set("health", "ok", Duration.ofSeconds(10));
            String result = (String) redisTemplate.opsForValue().get("health");
            boolean healthy = "ok".equals(result);

            redisAvailable = healthy;
            if (healthy) redisTemplate.delete("health");

            return healthy;
        } catch (RedisConnectionFailureException e) {
            redisAvailable = false;
            return false;
        } catch (Exception e) {
            redisAvailable = false;
            return false;
        }
    }

    /**
     * Returns Redis connection status
     */
    public String getRedisStatus() {
        if (redisTemplate == null) return "RedisTemplate is null";
        return redisAvailable ? "Available" : "Unavailable";
    }


    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }
}
