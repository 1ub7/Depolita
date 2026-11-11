package Depolita.domain.app.service;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.exception.AppNotFoundException;
import Depolita.domain.app.exception.DockerfileNotFoundException;
import Depolita.domain.app.repository.AppRepository;
import Depolita.global.exception.ErrorCode;
import Depolita.infra.ContainerManager;
import Depolita.infra.GitCloner;
import Depolita.infra.ImageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

@Slf4j
@Service
public class DeployService {
    private final AppRepository appRepository;
    private final GitCloner gitCloner;
    private final ImageBuilder imageBuilder;
    private final ContainerManager containerManager;
    private final Executor deployExecutor;
    private final TransactionTemplate newTransaction;

    public DeployService(AppRepository appRepository,
                         GitCloner gitCloner,
                         ImageBuilder imageBuilder,
                         ContainerManager containerManager,
                         @Qualifier("deployExecutor") Executor deployExecutor,
                         PlatformTransactionManager transactionManager) {
        this.appRepository = appRepository;
        this.gitCloner = gitCloner;
        this.imageBuilder = imageBuilder;
        this.containerManager = containerManager;
        this.deployExecutor = deployExecutor;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static String imageTag(Long appId) {
        return "depolita/app-" + appId + ":latest";
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppCreated(AppCreatedEvent event) {
        Long appId = event.appId();
        try {
            deployExecutor.execute(() -> deploy(appId));
        } catch (RejectedExecutionException e) {
            log.warn("[deploy] appId={} 배포 대기열 초과로 거절", appId);
            markFailed(appId, ErrorCode.DEPLOY_QUEUE_FULL.getMessage());
        }
    }

    private void deploy(Long appId) {
        Path workDir = null;
        String containerId = null;

        try {
            App app = appRepository.findById(appId)
                    .orElseThrow(AppNotFoundException::new);

            // 1. Clone (생성 시 상태가 이미 CLONING)
            workDir = gitCloner.clone(app.getRepositoryUrl());
            if (!Files.exists(workDir.resolve("Dockerfile"))) {
                throw new DockerfileNotFoundException();
            }

            // 2. Build
            updateApp(appId, App::startBuilding);
            imageBuilder.build(workDir, imageTag(appId));

            // 3. Run
            containerId = containerManager.run(
                    imageTag(appId),
                    "depolita-app-" + appId,
                    app.getAssignedPort(),
                    app.getInternalPort()
            );
            String runningId = containerId;
            updateApp(appId, a -> a.run(runningId));
            log.info("[deploy] appId={} RUNNING on port {}", appId, app.getAssignedPort());

        } catch (Exception e) {
            log.error("[deploy] appId={} FAILED", appId, e);
            // 컨테이너는 떴는데 상태 저장이 실패한 경우: 포트가 반납되므로 컨테이너도 지워야 충돌이 안 남
            if (containerId != null) {
                removeQuietly(containerId);
            }
            markFailed(appId, e.getMessage());
        } finally {
            gitCloner.cleanup(workDir); // null이면 내부에서 무시
        }
    }

    private void updateApp(Long appId, Consumer<App> change) {
        newTransaction.executeWithoutResult(status ->
                appRepository.findById(appId).ifPresent(change) // 배포 중 삭제된 경우 무시
        );
    }

    private void markFailed(Long appId, String reason) {
        String message = (reason == null || reason.isBlank()) ? "알 수 없는 오류" : reason;
        try {
            updateApp(appId, a -> a.fail(message)); // 길이 제한은 App.fail()이 처리
        } catch (Exception ex) {
            log.error("[deploy] appId={} 실패 상태 기록 불가", appId, ex);
        }
    }

    private void removeQuietly(String containerId) {
        try {
            containerManager.remove(containerId);
        } catch (Exception ex) {
            log.error("[deploy] 컨테이너 정리 실패 containerId={}", containerId, ex);
        }
    }
}
