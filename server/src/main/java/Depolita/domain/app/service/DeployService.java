package Depolita.domain.app.service;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.exception.AppNotFoundException;
import Depolita.domain.app.exception.DockerfileNotFoundException;
import Depolita.domain.app.port.ContainerRuntime;
import Depolita.domain.app.port.FetchedSource;
import Depolita.domain.app.port.ImageBuilder;
import Depolita.domain.app.port.SourceFetcher;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.deployment.service.DeploymentRecorder;
import Depolita.global.exception.ErrorCode;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class DeployService {
    private final AppRepository appRepository;
    private final SourceFetcher sourceFetcher;
    private final ImageBuilder imageBuilder;
    private final ContainerRuntime containerRuntime;
    private final DeploymentRecorder deploymentRecorder;
    private final Executor deployExecutor;
    private final TransactionTemplate newTransaction;

    public DeployService(AppRepository appRepository,
                         SourceFetcher sourceFetcher,
                         ImageBuilder imageBuilder,
                         ContainerRuntime containerRuntime,
                         DeploymentRecorder deploymentRecorder,
                         @Qualifier("deployExecutor") Executor deployExecutor,
                         PlatformTransactionManager transactionManager) {
        this.appRepository = appRepository;
        this.sourceFetcher = sourceFetcher;
        this.imageBuilder = imageBuilder;
        this.containerRuntime = containerRuntime;
        this.deploymentRecorder = deploymentRecorder;
        this.deployExecutor = deployExecutor;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static String imageTag(Long appId) {
        return "depolita/app-" + appId + ":latest";
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeployRequested(DeployRequestedEvent event) {
        Long appId = event.appId();
        Long deploymentId = deploymentRecorder.start(appId, event.trigger());
        try {
            deployExecutor.execute(() -> deploy(appId, deploymentId));
        } catch (RejectedExecutionException e) {
            log.warn("[deploy] appId={} 배포 대기열 초과로 거절", appId);
            fail(appId, deploymentId, ErrorCode.DEPLOY_QUEUE_FULL.getMessage());
        }
    }

    private void deploy(Long appId, Long deploymentId) {
        Path workDir = null;
        String containerId = null;

        try {
            App app = appRepository.findById(appId)
                    .orElseThrow(AppNotFoundException::new);

            // 1. Clone (생성·재배포 요청 시 상태가 이미 CLONING)
            long cloneStartedAt = System.nanoTime();
            FetchedSource source = sourceFetcher.fetch(app.getRepositoryUrl());
            workDir = source.dir();
            deploymentRecorder.recordClone(deploymentId, source.commitSha(), elapsedMs(cloneStartedAt));
            if (!Files.exists(workDir.resolve("Dockerfile"))) {
                throw new DockerfileNotFoundException();
            }

            // 2. Build
            updateApp(appId, App::startBuilding);
            long buildStartedAt = System.nanoTime();
            imageBuilder.build(workDir, imageTag(appId));
            deploymentRecorder.recordBuild(deploymentId, elapsedMs(buildStartedAt));

            // 3. Run
            // 재배포라면 같은 컨테이너 이름(depolita-app-{id})과 포트를 쓰는 이전 컨테이너를 먼저 지운다.
            // 빌드가 끝날 때까지는 이전 버전이 계속 서비스된다.
            if (app.getContainerId() != null) {
                containerRuntime.remove(app.getContainerId());
            }
            containerId = containerRuntime.run(
                    appId,
                    imageTag(appId),
                    "depolita-app-" + appId,
                    app.getAssignedPort(),
                    app.getInternalPort()
            );
            String runningId = containerId;
            updateApp(appId, a -> a.run(runningId));
            deploymentRecorder.succeed(deploymentId);
            log.info("[deploy] appId={} RUNNING on port {}", appId, app.getAssignedPort());

        } catch (Exception e) {
            log.error("[deploy] appId={} FAILED", appId, e);
            if (containerId != null) {
                removeQuietly(containerId);
            }
            fail(appId, deploymentId, e.getMessage());
        } finally {
            sourceFetcher.cleanup(workDir);
        }
    }

    private void updateApp(Long appId, Consumer<App> change) {
        newTransaction.executeWithoutResult(status ->
                appRepository.findById(appId).ifPresent(change)
        );
    }

    private void fail(Long appId, Long deploymentId, String reason) {
        String message = (reason == null || reason.isBlank()) ? "알 수 없는 오류" : reason;
        try {
            updateApp(appId, a -> a.fail(message));
        } catch (Exception ex) {
            log.error("[deploy] appId={} 실패 상태 기록 불가", appId, ex);
        }
        deploymentRecorder.fail(deploymentId, message);
    }

    private void removeQuietly(String containerId) {
        try {
            containerRuntime.remove(containerId);
        } catch (Exception ex) {
            log.error("[deploy] 컨테이너 정리 실패 containerId={}", containerId, ex);
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
