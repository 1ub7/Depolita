package Depolita.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("depolita")
public record DepolitaProperties(
        String apiKey,
        String publicHost
) {
    public DepolitaProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        publicHost = (publicHost == null || publicHost.isBlank()) ? "localhost" : publicHost.trim();
    }
}
