package Depolita.domain.app.service;

import Depolita.domain.app.port.Notifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AppNotificationListenerTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    private final Notifier notifier = mock(Notifier.class);
    private final AppNotificationListener listener = new AppNotificationListener(notifier);

    @Test
    @DisplayName("성공 메시지에는 앱 이름, 짧은 커밋 SHA, 포트가 들어간다")
    void successMessage() {
        String message = AppNotificationListener.format(
                DeploymentFinishedEvent.succeeded(1L, "my-app", SHA, 10001));

        assertTrue(message.contains("my-app"));
        assertTrue(message.contains("0123456"));
        assertTrue(message.contains("10001"));
    }

    @Test
    @DisplayName("앱 이름을 모르면 ID로 표시한다")
    void unknownAppName() {
        String message = AppNotificationListener.format(
                DeploymentFinishedEvent.failed(7L, null, null, "배포 대기열이 가득 찼습니다."));

        assertTrue(message.contains("#7"));
        assertTrue(message.contains("배포 대기열"));
    }

    @Test
    @DisplayName("알림 전송이 실패해도 예외를 밖으로 던지지 않는다 (배포·재조정에 영향 없음)")
    void notifierFailureIsSwallowed() {
        doThrow(new RuntimeException("Discord 503")).when(notifier).send(anyString());

        assertDoesNotThrow(() -> listener.onDeploymentFinished(
                DeploymentFinishedEvent.succeeded(1L, "my-app", SHA, 10001)));
        assertDoesNotThrow(() -> listener.onAppCrashed(new AppCrashedEvent(1L, "my-app", "OOM")));
        verify(notifier, org.mockito.Mockito.times(2)).send(anyString());
    }
}
