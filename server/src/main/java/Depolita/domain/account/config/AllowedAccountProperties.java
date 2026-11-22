package Depolita.domain.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties
public record AllowedAccountProperties(
        Set<String> allowedAccounts
) {
    public static final String ALL = "*";

    public AllowedAccountProperties {
        allowedAccounts = allowedAccounts == null ? Set.of()
                :allowedAccounts.stream()
                 .map(String::trim)
                 .filter(account ->!account.isEmpty())
                 .map(String::toLowerCase)
                 .collect(Collectors.toUnmodifiableSet());
    }

    public boolean allowsAll() {
        return allowedAccounts.contains(ALL);
    }

    public boolean allows(String owner) {
        return allowsAll() || allowedAccounts.contains(owner);
    }
}
