package Depolita.domain.deployment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 배포 1회의 기록.
 * App 과 JPA 연관관계를 맺지 않고 appId 만 저장한다. 앱 삭제 시 이력 삭제는 DB 의 ON DELETE CASCADE 가 처리한다.
 */
@Entity
@Table(name = "deployments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deployment {
    public static final int FAILURE_REASON_MAX_LENGTH = 1000;
    public static final int COMMIT_SHA_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long appId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private DeployTrigger trigger;

    @Column(length = COMMIT_SHA_LENGTH)
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DeploymentStatus status;

    @Column(length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    private Long cloneMs;

    private Long buildMs;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Deployment(Long appId, DeployTrigger trigger, LocalDateTime startedAt) {
        this.appId = appId;
        this.trigger = trigger;
        this.status = DeploymentStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    public static Deployment start(Long appId, DeployTrigger trigger, LocalDateTime now) {
        return new Deployment(appId, trigger, now);
    }

    public void recordClone(String commitSha, long elapsedMs) {
        this.commitSha = commitSha;
        this.cloneMs = elapsedMs;
    }

    public void recordBuild(long elapsedMs) {
        this.buildMs = elapsedMs;
    }

    public void succeed(LocalDateTime now) {
        finish(DeploymentStatus.SUCCEEDED, now);
    }

    public void fail(String reason, LocalDateTime now) {
        this.failureReason = truncate(reason);
        finish(DeploymentStatus.FAILED, now);
    }

    public boolean isFinished() {
        return status != DeploymentStatus.IN_PROGRESS;
    }

    private void finish(DeploymentStatus result, LocalDateTime now) {
        if (isFinished()) {
            return; // 이미 끝난 기록은 덮어쓰지 않는다
        }
        this.status = result;
        this.finishedAt = now;
    }

    private static String truncate(String reason) {
        if (reason == null) return null;
        return reason.length() > FAILURE_REASON_MAX_LENGTH
                ? reason.substring(0, FAILURE_REASON_MAX_LENGTH)
                : reason;
    }
}
