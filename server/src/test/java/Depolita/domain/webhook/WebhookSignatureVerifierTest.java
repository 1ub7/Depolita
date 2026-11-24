package Depolita.domain.webhook;

import Depolita.domain.webhook.config.WebhookProperties;
import Depolita.domain.webhook.support.WebhookSignatureVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {
    private static final String SECRET = "It's a Secret to Everybody";
    private static final byte[] BODY = "Hello, World!".getBytes(StandardCharsets.UTF_8);

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(new WebhookProperties(SECRET));

    static String sign(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    @DisplayName("GitHub 공식 문서의 예시 값과 일치한다")
    void githubDocsExample() {
        // https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries#testing-the-webhook-payload-validation
        String expected = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

        assertTrue(verifier.verify(BODY, expected));
    }

    @Test
    @DisplayName("같은 Secret으로 서명하면 통과")
    void validSignature() throws Exception {
        assertTrue(verifier.verify(BODY, sign(SECRET, BODY)));
    }

    @Test
    @DisplayName("본문이 1바이트라도 바뀌면 거절")
    void tamperedBody() throws Exception {
        String signature = sign(SECRET, BODY);

        assertFalse(verifier.verify("Hello, World?".getBytes(StandardCharsets.UTF_8), signature));
    }

    @Test
    @DisplayName("다른 Secret으로 서명하면 거절")
    void wrongSecret() throws Exception {
        assertFalse(verifier.verify(BODY, sign("guess", BODY)));
    }

    @Test
    @DisplayName("헤더가 없거나 sha256= 접두사가 없으면 거절")
    void missingOrMalformedHeader() throws Exception {
        assertFalse(verifier.verify(BODY, null));
        assertFalse(verifier.verify(BODY, sign(SECRET, BODY).substring("sha256=".length())));
    }

    @Test
    @DisplayName("서버에 Secret이 설정되지 않았으면 무엇이든 거절 (fail-closed)")
    void notConfigured() throws Exception {
        WebhookSignatureVerifier unconfigured = new WebhookSignatureVerifier(new WebhookProperties(""));

        assertFalse(unconfigured.verify(BODY, sign(SECRET, BODY)));
    }
}
