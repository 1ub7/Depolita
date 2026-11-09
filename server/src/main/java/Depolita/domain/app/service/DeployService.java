package Depolita.domain.app.service;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.repository.AppRepository;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import Depolita.infra.ContainerManager;
import Depolita.infra.GitCloner;
import Depolita.infra.ImageBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeployService {
    private static final int MAX_REASON_LENGTH = 500;

    private final AppRepository appRepository;
    private final GitCloner gitCloner;
    private final ImageBuilder imageBuilder;
    private final ContainerManager containerManager;
    private final TransactionTemplate transactionTemplate;

    public static String imageTag(Long appId) {
        return "depolita/app-" + appId + ":latest";
    }

    @Async("deployExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deploy(AppCreatedEvent event) {
        Long appId = event.appId();
        Path workDir = null;

        try {
            App app = appRepository.findById(appId)
                    .orElseThrow(() -> new CustomException(ErrorCode.APP_NOT_FOUND));

            // 1. Clone (생성 시 상태가 이미 CLONING)
            workDir = gitCloner.clone(app.getRepositoryUrl());
            if (!Files.exists(workDir.resolve("Dockerfile"))) {
                throw new CustomException(ErrorCode.DOCKERFILE_NOT_FOUND);
            }

            // 2. Build
            updateApp(appId, a -> a.changeStatus(AppStatus.BUILDING));
            imageBuilder.build(workDir, imageTag(appId));

            // 3. Run
            String containerId = containerManager.run(
                    imageTag(appId),
                    "depolita-app-" + appId,
                    app.getAssignedPort(),
                    app.getInternalPort()
            );
            updateApp(appId, a -> a.markRunning(containerId));
            log.info("[deploy] appId={} RUNNING on port {}", appId, app.getAssignedPort());

        } catch (Exception e) {
            log.error("[deploy] appId={} FAILED", appId, e);
            markFailed(appId, e);
        } finally {
            gitCloner.cleanup(workDir); // null이면 내부에서 무시
        }
    }

    private void updateApp(Long appId, Consumer<App> change) {
        transactionTemplate.executeWithoutResult(status ->
                appRepository.findById(appId).ifPresent(change) // 배포 중 삭제된 경우 무시
        );
    }

    private void markFailed(Long appId, Exception e) {
        try {
            updateApp(appId, a -> a.markFailed(truncate(e.getMessage())));
        } catch (Exception ex) {
            log.error("[deploy] appId={} 실패 상태 기록 불가", appId, ex);
        }
    }

    private String truncate(String message) {
        if (message == null) return "알 수 없는 오류";
        return message.length() > MAX_REASON_LENGTH ? message.substring(0, MAX_REASON_LENGTH) : message;
    }
}
