package Depolita.domain.webhook.support;

import Depolita.domain.webhook.config.WebhookProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * GitHub Webhook 서명(X-Hub-Signature-256) 검증.
 * GitHub 는 요청 본문 원본 바이트를 Secret 으로 HMAC-SHA256 한 값을 "sha256=<hex>" 형태로 보낸다.
 * https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries
 */
@Component
public class WebhookSignatureVerifier {
    public static final String HEADER = "X-Hub-Signature-256";
    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public WebhookSignatureVerifier(WebhookProperties properties) {
        this.secret = properties.githubSecret().getBytes(StandardCharsets.UTF_8);
    }

    public boolean verify(byte[] body, String signatureHeader) {
        if (secret.length == 0 || body == null || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] expected = (PREFIX + HexFormat.of().formatHex(hmac(body))).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signatureHeader.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
