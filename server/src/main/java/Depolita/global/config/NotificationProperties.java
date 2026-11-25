package Depolita.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param discordWebhookUrl Discord 채널 설정 → 연동 → 웹후크 URL. 비어 있으면 알림을 보내지 않는다
 */
@ConfigurationProperties("depolita.notification")
public record NotificationProperties(String discordWebhookUrl) {
    public NotificationProperties {
        discordWebhookUrl = discordWebhookUrl == null ? "" : discordWebhookUrl.trim();
    }

    public boolean discordEnabled() {
        return !discordWebhookUrl.isEmpty();
    }
}
