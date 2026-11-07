package com.SD.RateLimiter.service;

import com.SD.RateLimiter.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
@RequiredArgsConstructor
public class RedisTokenBucketService {

    private final JedisPool jedisPool;
    private final RateLimiterProperties properties;

    //these are the prefixes + //pattern
    //rate_limiter:{type}:{client_id}
    private final String TOKEN_KEY_PREFIX = "rate_limiter:tokens:";
    private final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    //rate_limiter:tokens:192.153.1.100 >"7" //token count for that ip
    //rate_limiter:last_refill:192.153.1.100 >"12345678"


    public boolean isAllowed(String clientId) {

        String tokenKey = TOKEN_KEY_PREFIX + clientId;  //rate_limiter:tokens:192.168.1.100 (key) for redis

        try (Jedis jedis = jedisPool.getResource()) {

            refillTokens(clientId, jedis);  //bucket refills over time (say 5 tokens/sec)

            String tokenStr = jedis.get(tokenKey);
            //for first req token key not exist, take token count from the properties
            long currentTokens = tokenStr != null ? Long.parseLong(tokenStr) : properties.getCapacity();  //Redis stores everything as text, so you get back a String like "7"

            if (currentTokens <= 0) {
                return false;
            }
            long decremented = jedis.decr(tokenKey);
            return (decremented >= 0);  //deduction+return done in one step to avoid the race condition
        }
    }

    public long getCapacity(String clientId) {
        return properties.getCapacity();
    }

    public long getAvailableTokens(String clientId) {

        String tokenKey = TOKEN_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenKey);
            return tokenStr != null ? Long.parseLong(tokenStr) : properties.getCapacity();
        }
    }

    private void refillTokens(String clientId, Jedis jedis) {

        String tokenKey = TOKEN_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        long now = System.currentTimeMillis();

        String lastRefillStr = jedis.get(lastRefillKey);

        if (lastRefillStr == null) {
            jedis.setex(tokenKey,3600, String.valueOf(properties.getCapacity())); //set the token 20
            jedis.setex(lastRefillKey,3600, String.valueOf(now));  //set the time now + //TTl - 1hour
            return;
        }

        long lastRefileTime = Long.parseLong(lastRefillStr);
        long elapsedTime = now - lastRefileTime;

        if (elapsedTime <= 0) {
            return;
        }

        long tokenToAdd = (elapsedTime * properties.getRefillRate()) / 1000;  // by 1k to convert into the sec

        if (tokenToAdd <= 0) {
            return;
        }

        String tokenStr = jedis.get(tokenKey);

        long currentTokens = Long.parseLong(tokenStr);
        long newTokens = Math.min(properties.getCapacity(), currentTokens + tokenToAdd);  //ensure that token is not more than the capacity

        jedis.setex(tokenKey, 3600,String.valueOf(newTokens));
        jedis.setex(lastRefillKey,3600, String.valueOf(now));


    }

}

