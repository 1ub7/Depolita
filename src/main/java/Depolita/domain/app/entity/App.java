package Depolita.domain.app.entity;

import Depolita.global.common.BaseTimeEntity;
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

@Entity
@Table(name = "apps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class App extends BaseTimeEntity {
    private static final int FAILURE_REASON_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, length = 500)
    private String repositoryUrl;

    @Column(nullable = false)
    private Integer internalPort;

    @Column(unique = true)
    private Integer assignedPort;

    @Column(length = 64)
    private String containerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppStatus status;

    @Column(length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    private App(String name, String repositoryUrl, Integer internalPort, Integer assignedPort) {
        this.name = name;
        this.repositoryUrl = repositoryUrl;
        this.internalPort = internalPort;
        this.assignedPort = assignedPort;
        this.status = AppStatus.CLONING;
    }

    public static App create(String name, String repositoryUrl,
                             Integer internalPort, Integer assignedPort) {
        return new App(name, repositoryUrl, internalPort, assignedPort);
    }

    public void startBuilding() {
        changeStatus(AppStatus.BUILDING);
    }

    public void run(String containerId) {
        changeStatus(AppStatus.RUNNING);
        this.containerId = containerId;
    }

    public void stop() {
        changeStatus(AppStatus.STOPPED);
        releasePort();
    }

    public void fail(String reason) {
        changeStatus(AppStatus.FAILED);
        this.failureReason = truncate(reason);
        releasePort();
    }

    private void changeStatus(AppStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "상태 전이 불가: %s -> %s (appId=%d)".formatted(this.status, next, this.id));
        }
        this.status = next;
    }

    private void releasePort() {
        this.assignedPort = null;
    }

    private String truncate(String reason) {
        if (reason == null) return null;
        return reason.length() > FAILURE_REASON_MAX_LENGTH
                ? reason.substring(0, FAILURE_REASON_MAX_LENGTH)
                : reason;
    }
}
