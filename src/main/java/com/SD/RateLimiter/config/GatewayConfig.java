package com.SD.RateLimiter.config;

import com.SD.RateLimiter.filters.TokenBucketRateLimiterFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final RateLimiterProperties rateLimiterProperties;
    private final TokenBucketRateLimiterFilter tokenBucketRateLimiterFilter;


    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder){

        return builder.routes()
                .route("api-route", r->r     //route's ID
                        .path("/api/**")    //path to route
                        .filters(f->f
                                        .stripPrefix(1)  //cuts the first path part (/api)
                                        .filter(tokenBucketRateLimiterFilter.apply(new TokenBucketRateLimiterFilter.Config()))
                                )
                        .uri(rateLimiterProperties.getApiServerUrl())  //the Backend url(downstream API)
                )
                .build();
    }
}



//RouteLocator is the gateway's routing table — it holds all your routes in one place.

