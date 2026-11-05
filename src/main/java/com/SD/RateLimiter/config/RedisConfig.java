package com.SD.RateLimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Component
@Data
@ConfigurationProperties(prefix = "spring.redis")
public class RedisConfig {

    private String host = "localhost";

    private int port = 6379;

    private int timeout = 2000 ;

    //Java client library for the redis
    //communicates b/w the redis and java
    @Bean
    public JedisPool getJedisPool() {
        //keep multiple connection ready to reuse
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50); //max no of connection in pool
        poolConfig.setMaxIdle(10);  //max Idle connection always there
        poolConfig.setMinIdle(5);  //min
        poolConfig.setTestOnBorrow(true); //test the connection before handing
        poolConfig.setTestOnReturn(true); //test connection when it's returning to pool
        return new JedisPool(poolConfig,host, port, timeout);
    }
}
