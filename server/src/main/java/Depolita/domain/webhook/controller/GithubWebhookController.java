package Depolita.domain.webhook.controller;

import Depolita.domain.webhook.dto.GithubPushPayload;
import Depolita.domain.webhook.dto.WebhookResponse;
import Depolita.domain.webhook.service.GithubWebhookService;
import Depolita.domain.webhook.support.WebhookSignatureVerifier;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * GitHub 저장소 Settings → Webhooks 에 등록하는 주소.
 * API 키 대신 HMAC 서명으로 인증한다 (ApiKeyFilter 의 PUBLIC_PATHS 참고).
 */
@RestController
@RequiredArgsConstructor
public class GithubWebhookController {
    public static final String PATH = "/api/webhooks/github";
    public static final String EVENT_HEADER = "X-GitHub-Event";

    private final WebhookSignatureVerifier signatureVerifier;
    private final GithubWebhookService webhookService;
    private final JsonMapper jsonMapper;

    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebhookResponse> receive(
            @RequestHeader(EVENT_HEADER) String event,
            @RequestHeader(value = WebhookSignatureVerifier.HEADER, required = false) String signature,
            @RequestBody byte[] body) {
        if (!signatureVerifier.verify(body, signature)) {
            throw new CustomException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
        return switch (event) {
            // Webhook 을 처음 등록하면 GitHub 가 ping 을 보낸다
            case "ping" -> ResponseEntity.ok(WebhookResponse.ignored("pong"));
            case "push" -> ResponseEntity.accepted()
                    .body(webhookService.handlePush(jsonMapper.readValue(body, GithubPushPayload.class)));
            default -> ResponseEntity.accepted()
                    .body(WebhookResponse.ignored("처리하지 않는 이벤트입니다: " + event));
        };
    }
}
