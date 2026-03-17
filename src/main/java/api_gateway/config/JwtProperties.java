package api_gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    private String authHeader;
    private String bearerPrefix;
    private String accessSecret;
    private Integer accessLifetime;
    private String refreshSecret;
    private Integer refreshLifetime;
    private List<String> ignoredPaths;
}
