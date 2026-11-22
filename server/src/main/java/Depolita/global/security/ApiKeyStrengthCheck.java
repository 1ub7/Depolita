package Depolita.global.security;

import Depolita.global.config.DepolitaProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Profile("prod")
public class ApiKeyStrengthCheck {
    public static final int MIN_LENGTH = 32;

    public ApiKeyStrengthCheck(DepolitaProperties properties) {
        validate(properties.apiKey());
    }

    static void validate(String apiKey) {
        int length = apiKey.getBytes(StandardCharsets.UTF_8).length;
        if (length < MIN_LENGTH) {
            throw new IllegalStateException(
                    "DEPOLITA_API_KEY는 %d바이트 이상이어야 합니다. (현재 %d) 생성: openssl rand -base64 32"
                            .formatted(MIN_LENGTH, length));
        }
    }
}
