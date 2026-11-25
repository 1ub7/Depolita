package Depolita.infra;

import Depolita.domain.app.port.Notifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Discord 채널 Webhook 으로 메시지를 보낸다.
 * https://discord.com/developers/docs/resources/webhook#execute-webhook
 */
public class DiscordNotifier implements Notifier {
    /** Discord 메시지 content 최대 길이 */
    static final int MAX_LENGTH = 2000;
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String webhookUrl;

    public DiscordNotifier(String webhookUrl) {
        this(RestClient.builder().requestFactory(requestFactory()).build(), webhookUrl);
    }

    DiscordNotifier(RestClient restClient, String webhookUrl) {
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void send(String message) {
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", truncate(message)))
                .retrieve()
                .toBodilessEntity();
    }

    static String truncate(String message) {
        return message.length() > MAX_LENGTH ? message.substring(0, MAX_LENGTH - 1) + "…" : message;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }
}
