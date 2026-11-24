package Depolita.domain.webhook.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param githubSecret GitHub 저장소 Webhook 설정에 넣은 Secret. 비어 있으면 모든 Webhook 요청을 거절한다 (fail-closed)
 */
@ConfigurationProperties("depolita.webhook")
public record WebhookProperties(String githubSecret) {
    private static final Logger log = LoggerFactory.getLogger(WebhookProperties.class);

    public WebhookProperties {
        githubSecret = githubSecret == null ? "" : githubSecret.trim();
        if (githubSecret.isEmpty()) {
            log.warn("[webhook] GITHUB_WEBHOOK_SECRET이 설정되지 않았습니다. GitHub Webhook 요청이 모두 거절됩니다.");
        }
    }
}
