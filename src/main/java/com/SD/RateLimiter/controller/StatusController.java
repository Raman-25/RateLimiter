package com.SD.RateLimiter.controller;


import com.SD.RateLimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.Map;

/**
 * Controller for health check and rate limit status endpoints.
 * These endpoints are not routed through the gateway filter.
**/

@RestController
@RequestMapping("/gateway")
@RequiredArgsConstructor
public class StatusController {

    private final RateLimiterService rateLimiterService;

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Rate Limiter Gateway"
        )));
    }

    @GetMapping("/rate-limit/status")
    public Mono<ResponseEntity<Map<String, Serializable>>> getRateLimitingStatus(ServerWebExchange exchange){

        String clientId = getClientId(exchange);
        return Mono.just(ResponseEntity.ok(Map.of(
                "clientId", clientId,
                "capacity",rateLimiterService.getCapacity(clientId),
                "availableTokens",rateLimiterService.getAvailableTokens(clientId)

        )));
    }

    private String getClientId(ServerWebExchange exchange) {

        ServerHttpRequest request = exchange.getRequest();

        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

}

