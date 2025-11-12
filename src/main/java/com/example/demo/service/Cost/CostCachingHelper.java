package com.example.demo.service.Cost;

import com.example.demo.model.Cost.*;
import com.example.demo.service.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  COST CACHING HELPER (with configurable TTLs)
 */
@Component
public class CostCachingHelper {
    private static final Logger logger = LoggerFactory.getLogger(CostCachingHelper.class);

    @Autowired(required = false)
    private CacheService cacheService;

    @Autowired
    private ObjectMapper objectMapper;

    //  CONFIGURABLE TTLs from application.yml
    @Value("${cache.ttl.pod-metrics:30}")
    private int podMetricsTtlSeconds;
    
    @Value("${cache.ttl.cost-analysis:300}")
    private int costAnalysisTtlSeconds;
    
    @Value("${cache.ttl.cluster-summary:600}")
    private int clusterSummaryTtlSeconds;
    
    @Value("${cache.ttl.recommendations:900}")
    private int recommendationsTtlSeconds;
    
    @Value("${cache.ttl.anomalies:300}")
    private int anomaliesTtlSeconds;
    
    @Value("${cache.ttl.forecast:3600}")
    private int forecastTtlSeconds;

    private static final String CACHE_PREFIX = "cost:";

    /**
     * Cache cost analysis for a namespace
     */
    public void cacheCostAnalysis(String namespace, CostAnalysis analysis) {
        if (cacheService == null) return;

        try {
            String key = CACHE_PREFIX + "analysis:" + namespace;
            String json = objectMapper.writeValueAsString(analysis);
            
            cacheService.getRedisTemplate()
                .opsForValue()
                .set(key, json, Duration.ofSeconds(costAnalysisTtlSeconds));
                
            logger.debug("✅ Cached cost analysis for {} (TTL: {}s)", namespace, costAnalysisTtlSeconds);
        } catch (Exception e) {
            logger.debug("Cache write failed: {}", e.getMessage());
        }
    }

    /**
     * Get cached cost analysis
     */
    public CostAnalysis getCachedCostAnalysis(String namespace) {
        if (cacheService == null) return null;

        try {
            String key = CACHE_PREFIX + "analysis:" + namespace;
            Object value = cacheService.getRedisTemplate()
                .opsForValue()
                .get(key);
                
            if (value == null) return null;

            CostAnalysis analysis = objectMapper.readValue(
                value.toString(), CostAnalysis.class);
            logger.debug("✅ Cache HIT for {} (TTL: {}s)", namespace, costAnalysisTtlSeconds);
            return analysis;
            
        } catch (Exception e) {
            logger.debug("Cache read failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Cache cluster summary
     */
    public void cacheClusterSummary(ClusterCostSummary summary) {
        if (cacheService == null) return;

        try {
            String key = CACHE_PREFIX + "cluster:summary";
            String json = objectMapper.writeValueAsString(summary);
            
            cacheService.getRedisTemplate()
                .opsForValue()
                .set(key, json, Duration.ofSeconds(clusterSummaryTtlSeconds));
                
            logger.debug("✅ Cached cluster summary (TTL: {}s)", clusterSummaryTtlSeconds);
        } catch (Exception e) {
            logger.debug("Cache write failed: {}", e.getMessage());
        }
    }

    /**
     * Get cached cluster summary
     */
    public ClusterCostSummary getCachedClusterSummary() {
        if (cacheService == null) return null;

        try {
            String key = CACHE_PREFIX + "cluster:summary";
            Object value = cacheService.getRedisTemplate()
                .opsForValue()
                .get(key);
                
            if (value == null) return null;

            ClusterCostSummary summary = objectMapper.readValue(
                value.toString(), ClusterCostSummary.class);
            logger.debug("✅ Cache HIT for cluster summary (TTL: {}s)", clusterSummaryTtlSeconds);
            return summary;
            
        } catch (Exception e) {
            logger.debug("Cache read failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Cache pod metrics
     */
    public void cachePodMetrics(String namespace, String podName, Map<String, String> metrics) {
        if (cacheService == null || metrics == null) return;

        try {
            String key = CACHE_PREFIX + "metrics:" + namespace + ":" + podName;
            String json = objectMapper.writeValueAsString(metrics);
            
            cacheService.getRedisTemplate()
                .opsForValue()
                .set(key, json, Duration.ofSeconds(podMetricsTtlSeconds));
                
        } catch (Exception e) {
            logger.debug("Cache write failed: {}", e.getMessage());
        }
    }

    /**
     * Get cached pod metrics
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getCachedPodMetrics(String namespace, String podName) {
        if (cacheService == null) return null;

        try {
            String key = CACHE_PREFIX + "metrics:" + namespace + ":" + podName;
            Object value = cacheService.getRedisTemplate()
                .opsForValue()
                .get(key);
                
            if (value == null) return null;

            return objectMapper.readValue(value.toString(), Map.class);
            
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Invalidate namespace cache
     */
    public void invalidateNamespace(String namespace) {
    if (cacheService == null) return;

    try {
        // FIX: Match both patterns with and without trailing colon
        String pattern1 = CACHE_PREFIX + "*:" + namespace;
        String pattern2 = CACHE_PREFIX + "*:" + namespace + "*";
        
        var keys1 = cacheService.getRedisTemplate().keys(pattern1);
        var keys2 = cacheService.getRedisTemplate().keys(pattern2);
        
        Set<String> allKeys = new HashSet<>();
        if (keys1 != null) allKeys.addAll(keys1);
        if (keys2 != null) allKeys.addAll(keys2);
        
        if (!allKeys.isEmpty()) {
            cacheService.getRedisTemplate().delete(allKeys);
            logger.info("Invalidated {} cache keys for {}", allKeys.size(), namespace);
        }
    } catch (Exception e) {
        logger.warn("Cache invalidation failed: {}", e.getMessage());
    }
}
}