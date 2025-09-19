// src/main/java/com/example/demo/config/CorsConfig.java
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Alternative: Specify exact origins if you need credentials
//adding a comment in a code file to test gitops
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(
                            "http://localhost:4200",           // Angular dev server
                            "http://localhost:8080",           // Local frontend
                            "http://monitoring.example.com",   // Your ingress domain
                            "http://192.168.49.2" // Minikube IP
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true); // Can be true with specific origins
            }
        };
    }
}