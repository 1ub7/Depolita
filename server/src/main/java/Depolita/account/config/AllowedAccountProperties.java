package Depolita.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties
public record AllowedAccountProperties(
        Set<String> allowedAccounts
) {
}
