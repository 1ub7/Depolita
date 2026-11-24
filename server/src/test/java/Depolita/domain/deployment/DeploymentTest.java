package Depolita.domain.deployment;

import Depolita.domain.deployment.entity.DeployTrigger;
import Depolita.domain.deployment.entity.Deployment;
import Depolita.domain.deployment.entity.DeploymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentTest {
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 24, 12, 0);

    @Test
    @DisplayName("시작하면 IN_PROGRESS이고 끝난 시각이 없다")
    void start() {
        Deployment d = Deployment.start(1L, DeployTrigger.MANUAL, T0);

        assertEquals(DeploymentStatus.IN_PROGRESS, d.getStatus());
        assertEquals(DeployTrigger.MANUAL, d.getTrigger());
        assertNull(d.getFinishedAt());
        assertFalse(d.isFinished());
    }

    @Test
    @DisplayName("단계별 기록 후 성공하면 SUCCEEDED와 끝난 시각이 남는다")
    void succeed() {
        Deployment d = Deployment.start(1L, DeployTrigger.INITIAL, T0);

        d.recordClone("abc123", 1200);
        d.recordBuild(45_000);
        d.succeed(T0.plusMinutes(1));

        assertEquals(DeploymentStatus.SUCCEEDED, d.getStatus());
        assertEquals("abc123", d.getCommitSha());
        assertEquals(1200L, d.getCloneMs());
        assertEquals(45_000L, d.getBuildMs());
        assertEquals(T0.plusMinutes(1), d.getFinishedAt());
    }

    @Test
    @DisplayName("실패 원인이 너무 길면 최대 길이로 자른다")
    void failTruncatesReason() {
        Deployment d = Deployment.start(1L, DeployTrigger.WEBHOOK, T0);

        d.fail("x".repeat(Deployment.FAILURE_REASON_MAX_LENGTH + 10), T0.plusSeconds(5));

        assertEquals(DeploymentStatus.FAILED, d.getStatus());
        assertEquals(Deployment.FAILURE_REASON_MAX_LENGTH, d.getFailureReason().length());
    }

    @Test
    @DisplayName("이미 끝난 기록은 다시 덮어쓰지 않는다")
    void finishedIsFinal() {
        Deployment d = Deployment.start(1L, DeployTrigger.MANUAL, T0);
        d.succeed(T0.plusSeconds(10));

        d.fail("늦게 도착한 실패", T0.plusSeconds(20));

        assertTrue(d.isFinished());
        assertEquals(DeploymentStatus.SUCCEEDED, d.getStatus());
        assertEquals(T0.plusSeconds(10), d.getFinishedAt());
    }
}
