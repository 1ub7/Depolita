package Depolita.domain.app.service;

import Depolita.domain.app.port.Notifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 배포 결과와 장애를 운영자에게 알린다.
 * 알림 전용 스레드풀(notifyExecutor)에서 돌기 때문에 Discord 가 느리거나 죽어도 배포 스레드는 기다리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppNotificationListener {
    private final Notifier notifier;

    @Async("notifyExecutor")
    @EventListener
    public void onDeploymentFinished(DeploymentFinishedEvent event) {
        send(format(event));
    }

    @Async("notifyExecutor")
    @EventListener
    public void onAppCrashed(AppCrashedEvent event) {
        send("🚨 **%s** 실행 중 장애로 중단됐습니다.\n> %s".formatted(label(event.appId(), event.appName()), event.reason()));
    }

    static String format(DeploymentFinishedEvent event) {
        String app = label(event.appId(), event.appName());
        String commit = event.commitSha() == null ? "" : " (`%s`)".formatted(shortSha(event.commitSha()));
        if (event.success()) {
            return "✅ **%s** 배포 성공%s → 포트 %d".formatted(app, commit, event.port());
        }
        return "❌ **%s** 배포 실패%s\n> %s".formatted(app, commit, event.reason());
    }

    private static String label(Long appId, String appName) {
        return appName != null ? appName : "앱 #" + appId;
    }

    private static String shortSha(String sha) {
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    private void send(String message) {
        try {
            notifier.send(message);
        } catch (Exception e) {
            log.warn("[notify] 알림 전송 실패: {}", e.getMessage());
        }
    }
}
