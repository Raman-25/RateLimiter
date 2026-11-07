package com.SD.RateLimiter.filters;
import com.SD.RateLimiter.service.RateLimiterService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;


//Client Request > GatewayFilter (intercepts request) > Check Rate Limit)

//Global Filters > applied to all routes
//Route Filters > applied to specific routes
//Custom Filters > your own implementation of

@Component
@RequiredArgsConstructor
public class TokenBucketRateLimiterFilter extends AbstractGatewayFilterFactory<TokenBucketRateLimiterFilter.Config>{

    private final RateLimiterService rateLimiterService;

    @Override //makes the Config object.
    public TokenBucketRateLimiterFilter.Config newConfig(){
        return new Config();
    }

    @Override  // actual filter that runs on every request
    public GatewayFilter apply(Config config) {

        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            String clientId = getClientId(request);


            //wrap the blocking call
            return
                    Mono.fromCallable(() -> rateLimiterService.isAllowed(clientId))
                            .subscribeOn(Schedulers.boundedElastic())  //force mono to run on the bounded elastic pool the extra working thread
                            .flatMap(allowed -> {
                                if (!allowed) {
                                    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON); //prase it as JSON

                                    addRateLimitHeaders(response, clientId);
                                    response.getHeaders().add("Retry-After", "1");

                                    String errorBody = String.format(
                                            "{\"error\":\"Rate limit exceeded\",\"clientId\":\"%s\"}",
                                            clientId
                                    );//Error message

                                    return response.writeWith(
                                            Mono.just(response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8)))
                                    ); //take the JSON text, turn to byte, wrap it for network
                                }

                                return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                                    addRateLimitHeaders(response, clientId);
                                }));//ALLOWED-passes the request to the next filter and add the headers
                            });
        };
    }


    private void addRateLimitHeaders(ServerHttpResponse response, String clientId) {

        response.getHeaders().add("X-RateLimit-Limit",
                String.valueOf(rateLimiterService.getCapacity(clientId)));

        response.getHeaders().add("X-RateLimit-Remaining",
                String.valueOf(rateLimiterService.getAvailableTokens(clientId)));
    }

    private String getClientId(ServerHttpRequest request) {

        // 1.)
        String xForwardFor = request.getHeaders().getFirst("X-Forwarded-For");
        if(xForwardFor != null && !xForwardFor.isEmpty()){
            return xForwardFor.split(",")[0].trim();
        }

        //2.) Fallback
        var remoteAddress = request.getRemoteAddress();  // InetSocketAddress — a small object holding IP + port together
        if(remoteAddress!=null && remoteAddress.getAddress() != null){
            return remoteAddress.getAddress().getHostAddress();  //we need to pass the ip as a string
        }

        //default
        return "unknown";
    }

    // Per-route settings for this filter.
    // Each route creates its own Config and passes it to apply().
    // Example: login route -> capacity 5, public route -> capacity 100.

    @Data
    public static class Config {

        // Max tokens this route's bucket can hold.
        private long loginCapacity = 10;

        // Tokens refilled per second for this route.
        private long loginRefillRate = 5;

    }

}

//[exchange] - request-response pair travelling through the gateway
//chain — the rest of the journey: next filters + the downstream API. Calling chain.filter(exchange) is "let it through
//The gateway runs on Netty with WebFlux — there is no servlet request there at all. Reactive imports

//Mono.just(x) — wraps a value you already have
//Mono.fromRunnable(() -> ...) — wraps an action with no result allowed → //downstream owns the body, we just stamp headers (action → fromRunnable). Denied → we own the body