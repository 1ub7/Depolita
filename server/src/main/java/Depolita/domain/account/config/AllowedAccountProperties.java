package Depolita.domain.account.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties
public record AllowedAccountProperties(
        Set<String> allowedAccounts
) {
    public static final String ALL = "*";
    private static final Logger log = LoggerFactory.getLogger(AllowedAccountProperties.class);

    public AllowedAccountProperties {
        allowedAccounts = allowedAccounts == null ? Set.of()
                :allowedAccounts.stream()
                 .map(String::trim)
                 .filter(account ->!account.isEmpty())
                 .map(String::toLowerCase)
                 .collect(Collectors.toUnmodifiableSet());
        if (allowedAccounts.contains(ALL)) {
            log.warn("[account] ALLOWED_ACCOUNTS=* : 모든 GitHub 계정의 배포를 허용합니다. API 키가 유일한 접근 제어입니다.");
        }
    }

    public boolean allowsAll() {
        return allowedAccounts.contains(ALL);
    }

    public boolean allows(String owner) {
        return allowsAll() || allowedAccounts.contains(owner);
    }
}
