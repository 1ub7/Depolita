package Depolita.global.config;

import Depolita.domain.app.port.Notifier;
import Depolita.infra.DiscordNotifier;
import Depolita.infra.NoopNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class NotifierConfig {
    @Bean
    public Notifier notifier(NotificationProperties properties) {
        if (!properties.discordEnabled()) {
            log.info("[notify] DISCORD_WEBHOOK_URL 미설정: 알림을 보내지 않습니다.");
            return new NoopNotifier();
        }
        return new DiscordNotifier(properties.discordWebhookUrl());
    }
}
