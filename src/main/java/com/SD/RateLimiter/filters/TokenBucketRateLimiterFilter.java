package com.SD.RateLimiter.filters;
import com.SD.RateLimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;


//Client Request > GatewayFilter (intercepts request) > Check Rate Limit)

//Global Filters > applied to all routes
//Route Filters > applied to specific routes
//Custom Filters > your own implementation of

@Component
@RequiredArgsConstructor
public class TokenBucketRateLimiterFilter extends AbstractGatewayFilterFactory<TokenBucketRateLimiterFilter.Config>{

    private final RateLimiterService rateLimiterService;

    @Override
    public TokenBucketRateLimiterFilter.Config newConfig(){
        return newConfig();
    }

    @Override  // actual filter that runs on every request
    public GatewayFilter apply(Config config){

        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            String clientId = getClientId(request);

            if(!rateLimiterService.isAllowed(clientId)){
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON); //this is JSON, parse it and pretty-print it

                addRateLimitHeaders(response, clientId);

                String errorBody = String.format(
                        "{\"error\":\"Rate limited exceeded\",\"clientId\":\"%s\"}",
                        clientId
                );  //error message

                return response.writeWith(
                        Mono.just(response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8)))
                );
                // write this data as the response body //take the JSON error text, turn it into bytes, wrap it for the network

            }

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                addRateLimitHeaders(response, clientId);
            })); //ALLOWED-passes the request to the next filter and add the headers
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
        var remoteAddress = request.getRemoteAddress();  //get original ip directly
        if(remoteAddress!=null && remoteAddress.getHostName() != null){
            return remoteAddress.getAddress().getHostAddress();
        }

        //default
        return "unknown";
    }

    public static class Config {
        // per-route settings go here
    }

}

//[exchange] - request-response pair travelling through the gateway
//chain — the rest of the journey: next filters + the downstream API. Calling chain.filter(exchange) is "let it through
//The gateway runs on Netty with WebFlux — there is no servlet request there at all. Reactive imports