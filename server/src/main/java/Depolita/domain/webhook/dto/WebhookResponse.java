package Depolita.domain.webhook.dto;

import java.util.List;

/**
 * GitHub 의 Recent Deliveries 화면에서 응답 본문을 볼 수 있으므로, 무엇을 했는지 남긴다.
 *
 * @param redeployed 재배포를 시작한 앱 ID
 * @param skipped    재배포하지 않은 앱 ID (배포 진행 중, 중지됨, 앱 개수 제한 등)
 * @param message    처리하지 않은 경우 그 이유
 */
public record WebhookResponse(List<Long> redeployed, List<Long> skipped, String message) {
    public static WebhookResponse ignored(String message) {
        return new WebhookResponse(List.of(), List.of(), message);
    }
}
