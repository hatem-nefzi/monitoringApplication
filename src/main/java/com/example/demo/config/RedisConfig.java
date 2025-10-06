package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);
    
    @Value("${redis.host:redis-service}")
    private String redisHost;
    
    @Value("${redis.port:6379}")
    private int redisPort;
    
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        logger.info("Configuring Redis connection to {}:{}", redisHost, redisPort);
        
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        
        // Test the connection
        try {
            factory.afterPropertiesSet();
            logger.info("Redis connection factory initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize Redis connection factory: {}", e.getMessage());
        }
        
        return factory;
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        logger.info("Creating RedisTemplate bean");
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Set serializers
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.afterPropertiesSet();
        
        // Test the template
        try {
            template.opsForValue().set("test-connection", "ok");
            String result = (String) template.opsForValue().get("test-connection");
            logger.info("Redis template test successful: {}", result);
            template.delete("test-connection");
        } catch (Exception e) {
            logger.error("Redis template test failed: {}", e.getMessage());
        }
        
        return template;
    }
}