package com.SD.RateLimiter.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    private long capacity = 10;
    private long refillRate = 5;
    private String apiServerUrl = "http://localhost:8081";
    private long timeout = 5000;

}
