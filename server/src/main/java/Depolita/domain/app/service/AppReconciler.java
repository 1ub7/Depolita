package Depolita.domain.app.service;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.app.port.ContainerRuntime;
import Depolita.domain.app.port.ContainerRuntime.ContainerInspection;
import Depolita.domain.app.port.ContainerRuntime.ContainerStatus;
import Depolita.domain.app.port.ContainerRuntime.ManagedContainer;
import Depolita.domain.app.port.ImageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AppReconciler {
    static final int CRASH_LOOP_THRESHOLD = 5;

    private final AppRepository appRepository;
    private final ContainerRuntime containerRuntime;
    private final ImageBuilder imageBuilder;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate tx;
    private final LocalDateTime startedAt;

    public AppReconciler(AppRepository appRepository,
                         ContainerRuntime containerRuntime,
                         ImageBuilder imageBuilder,
                         ApplicationEventPublisher eventPublisher,
                         PlatformTransactionManager transactionManager) {
        this.appRepository = appRepository;
        this.containerRuntime = containerRuntime;
        this.imageBuilder = imageBuilder;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
        this.startedAt = LocalDateTime.now();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        failInterruptedDeployments();
        try {
            reconcile();
        } catch (LinkageError e) {
            log.error("[reconcile] Docker 클라이언트를 초기화할 수 없습니다. DOCKER_HOST와 실행 환경(OS/아키텍처)을 확인하세요.", e);
        }
    }

    @Scheduled(initialDelayString = "${depolita.reconcile-interval-ms:30000}",
            fixedDelayString = "${depolita.reconcile-interval-ms:30000}")
    public void scheduledReconcile() {
        reconcile();
    }
    void failInterruptedDeployments() {
        tx.executeWithoutResult(status -> {
            List<App> stuck = appRepository.findAllByStatusInAndUpdatedAtBefore(
                    List.of(AppStatus.CLONING, AppStatus.BUILDING), startedAt);
            stuck.forEach(app -> app.fail("서버 재시작으로 배포가 중단되었습니다. 다시 배포해주세요."));
            if (!stuck.isEmpty()) {
                log.warn("[reconcile] 중단된 배포 {}건을 FAILED로 변경", stuck.size());
            }
        });
    }

    synchronized void reconcile() {
        try {
            reconcileApps();
            removeOrphanContainers();
        } catch (Exception e) {
            log.error("[reconcile] 재조정 실패, 다음 주기에 재시도", e);
        }
    }

    private void reconcileApps() {
        List<App> apps = appRepository.findAllByStatusIn(List.of(AppStatus.RUNNING, AppStatus.STOPPED));
        for (App app : apps) {
            try {
                if (app.getStatus() == AppStatus.RUNNING) {
                    reconcileRunning(app.getId(), app.getContainerId());
                } else {
                    reconcileStopped(app.getId(), app.getContainerId());
                }
            } catch (Exception e) {
                log.warn("[reconcile] appId={} 처리 실패", app.getId(), e);
            }
        }
    }

    private void reconcileRunning(Long appId, String containerId) {
        String reason = failureReason(containerRuntime.inspect(containerId));
        if (reason == null) {
            return;
        }
        // FAILED 로 바꾼 경우에만 앱 이름을 돌려받는다 (알림에 사용)
        String crashedAppName = tx.execute(status -> appRepository.findById(appId)
                .filter(app -> app.getStatus() == AppStatus.RUNNING)
                .map(app -> {
                    app.fail(reason);
                    return app.getName();
                })
                .orElse(null));
        if (crashedAppName != null) {
            if (containerId != null) {
                containerRuntime.remove(containerId);
            }
            log.warn("[reconcile] appId={} RUNNING → FAILED: {}", appId, reason);
            eventPublisher.publishEvent(new AppCrashedEvent(appId, crashedAppName, reason));
        }
    }

    private void reconcileStopped(Long appId, String containerId) {
        ContainerStatus actual = containerRuntime.inspect(containerId).status();
        if (actual == ContainerStatus.RUNNING
                || actual == ContainerStatus.RESTARTING) {
            containerRuntime.stop(containerId);
            log.warn("[reconcile] appId={} STOPPED인데 컨테이너가 실행 중이라 다시 중지", appId);
        }
    }

    static String failureReason(ContainerInspection inspection) {
        return switch (inspection.status()) {
            case RUNNING -> null;
            case RESTARTING -> inspection.restartCount() >= CRASH_LOOP_THRESHOLD
                    ? "컨테이너가 계속 재시작되고 있습니다(" + inspection.restartCount() + "회). 앱 로그를 확인해주세요."
                    : null;
            case EXITED -> inspection.oomKilled()
                    ? "메모리 제한(512MB)을 넘어서 컨테이너가 종료되었습니다."
                    : "컨테이너가 종료되었습니다.";
            case MISSING -> "컨테이너를 찾을 수 없습니다. 외부에서 삭제되었을 수 있습니다.";
        };
    }

    private void removeOrphanContainers() {
        List<ManagedContainer> containers = containerRuntime.listManaged();
        Map<Long, App> apps = appRepository.findAll().stream()
                .collect(Collectors.toMap(App::getId, Function.identity()));

        for (ManagedContainer container : containers) {
            App app = apps.get(container.appId());
            if (isOwned(app, container.id())) {
                continue;
            }
            try {
                containerRuntime.remove(container.id());
                if (app == null) {
                    imageBuilder.remove(DeployService.imageTag(container.appId()));
                }
                log.warn("[reconcile] 고아 컨테이너 삭제 containerId={} appId={}", container.id(), container.appId());
            } catch (Exception e) {
                log.warn("[reconcile] 고아 컨테이너 삭제 실패 containerId={}", container.id(), e);
            }
        }
    }

    private boolean isOwned(App app, String containerId) {
        if (app == null) {
            return false;
        }
        if (app.isInProgress()) {
            return true;
        }
        return app.getStatus() != AppStatus.FAILED && containerId.equals(app.getContainerId());
    }
}
