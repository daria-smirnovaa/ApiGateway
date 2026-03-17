package api_gateway.filters;

import api_gateway.config.JwtProperties;
import api_gateway.service.JwtTokenValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtTokenValidator jwtTokenValidator;
    private final JwtProperties jwtProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(JwtTokenValidator jwtTokenValidator, JwtProperties jwtProperties) {
        super(Config.class);
        this.jwtTokenValidator = jwtTokenValidator;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();
            if (isIgnoredPath(path)) {
                return chain.filter(exchange);
            }
            String authHeader = request.getHeaders().getFirst(jwtProperties.getAuthHeader());
            if (authHeader == null || !authHeader.startsWith(jwtProperties.getBearerPrefix())) {
                log.warn("Missing or invalid Authorization header for path: {}", path);
                return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
            }
            String token = authHeader.substring(jwtProperties.getBearerPrefix().length()).trim();
            if (!jwtTokenValidator.validateToken(token)) {
                log.warn("Invalid JWT token for path: {}", path);
                return unauthorizedResponse(exchange, "Invalid JWT token");
            }
            String username = jwtTokenValidator.getUsernameFromToken(token);
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header("X-User-Name", username)
                    .build();
            log.debug("JWT validation successful for user: {} on path: {}", username, path);
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    private boolean isIgnoredPath(String path) {
        return jwtProperties.getIgnoredPaths().stream()
                .anyMatch(ignoredPath -> pathMatcher.match(ignoredPath, path));
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {

    }
}