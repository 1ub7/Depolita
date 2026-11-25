package Depolita;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.app.service.DeployRequestedEvent;
import Depolita.domain.app.service.DeployService;
import Depolita.domain.app.service.DeploymentFinishedEvent;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import Depolita.domain.app.port.ContainerRuntime;
import Depolita.domain.app.port.ImageBuilder;
import Depolita.domain.app.port.FetchedSource;
import Depolita.domain.app.port.SourceFetcher;
import Depolita.domain.deployment.entity.DeployTrigger;
import Depolita.domain.deployment.service.DeploymentRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeployServiceTest {
    private static final Long APP_ID = 1L;
    private static final Long DEPLOYMENT_ID = 100L;
    private static final String COMMIT_SHA = "0123456789abcdef0123456789abcdef01234567";

    @TempDir
    Path workDir;

    private final AppRepository appRepository = mock(AppRepository.class);
    private final SourceFetcher sourceFetcher = mock(SourceFetcher.class);
    private final ImageBuilder imageBuilder = mock(ImageBuilder.class);
    private final ContainerRuntime containerRuntime = mock(ContainerRuntime.class);
    private final DeploymentRecorder recorder = mock(DeploymentRecorder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    private App app;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(recorder.start(eq(APP_ID), any())).thenReturn(DEPLOYMENT_ID);
        app = App.create("my-app", "https://github.com/1ub7/demo.git", 8080, 10001);
    }

    private DeployService deployService(Executor executor) {
        return new DeployService(appRepository, sourceFetcher, imageBuilder, containerRuntime, recorder, eventPublisher,
                executor, txManager);
    }

    private FetchedSource source() {
        return new FetchedSource(workDir, COMMIT_SHA);
    }

    private void deploy() {
        deployService(Runnable::run).onDeployRequested(new DeployRequestedEvent(APP_ID, DeployTrigger.MANUAL));
    }

    private void givenDockerfile() throws IOException {
        Files.writeString(workDir.resolve("Dockerfile"), "FROM alpine");
    }

    @Test
    @DisplayName("정상 흐름: RUNNING이 되고 컨테이너 ID가 저장된다")
    void success() throws IOException {
        givenDockerfile();
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenReturn(source());
        when(containerRuntime.run(eq(APP_ID), anyString(), anyString(), anyInt(), anyInt())).thenReturn("c-1");

        deploy();

        assertEquals(AppStatus.RUNNING, app.getStatus());
        assertEquals("c-1", app.getContainerId());
        assertEquals(10001, app.getAssignedPort());
        verify(sourceFetcher).cleanup(workDir);
        verify(recorder).recordClone(eq(DEPLOYMENT_ID), eq(COMMIT_SHA), anyLong());
        verify(recorder).recordBuild(eq(DEPLOYMENT_ID), anyLong());
        verify(recorder).succeed(DEPLOYMENT_ID);
        verify(eventPublisher).publishEvent(DeploymentFinishedEvent.succeeded(APP_ID, "my-app", COMMIT_SHA, 10001));
    }

    @Test
    @DisplayName("clone 실패 → FAILED, 포트 반납, 빌드·실행은 시도하지 않음")
    void cloneFailure() {
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenThrow(new CustomException(ErrorCode.GIT_CLONE_FAILED));

        deploy();

        assertEquals(AppStatus.FAILED, app.getStatus());
        assertNull(app.getAssignedPort());
        verifyNoInteractions(imageBuilder, containerRuntime);
        verify(recorder).fail(eq(DEPLOYMENT_ID), anyString());
        verify(recorder, never()).succeed(anyLong());
        verify(eventPublisher).publishEvent(DeploymentFinishedEvent.failed(
                APP_ID, "my-app", null, ErrorCode.GIT_CLONE_FAILED.getMessage()));
    }

    @Test
    @DisplayName("Dockerfile 없음 → FAILED, 복제한 폴더는 정리")
    void dockerfileMissing() {
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenReturn(source()); // 빈 폴더

        deploy();

        assertEquals(AppStatus.FAILED, app.getStatus());
        assertEquals(ErrorCode.DOCKERFILE_NOT_FOUND.getMessage(), app.getFailureReason());
        verify(sourceFetcher).cleanup(workDir);
        verifyNoInteractions(imageBuilder);
    }

    @Test
    @DisplayName("빌드 실패 → FAILED, 컨테이너는 만들지 않음")
    void buildFailure() throws IOException {
        givenDockerfile();
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenReturn(source());
        when(imageBuilder.build(any(), anyString()))
                .thenThrow(new CustomException(ErrorCode.IMAGE_BUILD_FAILED, "npm ERR!", new RuntimeException()));

        deploy();

        assertEquals(AppStatus.FAILED, app.getStatus());
        verifyNoInteractions(containerRuntime);
    }

    @Test
    @DisplayName("컨테이너는 떴는데 RUNNING 저장이 실패 → 컨테이너를 지우고 FAILED (포트 충돌 방지)")
    void saveFailureAfterContainerStarted() throws IOException {
        givenDockerfile();
        when(appRepository.findById(APP_ID))
                .thenReturn(Optional.of(app))
                .thenReturn(Optional.of(app))
                .thenThrow(new RuntimeException("DB 연결 끊김"))
                .thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenReturn(source());
        when(containerRuntime.run(eq(APP_ID), anyString(), anyString(), anyInt(), anyInt())).thenReturn("c-1");

        deploy();

        verify(containerRuntime).remove("c-1");
        assertEquals(AppStatus.FAILED, app.getStatus());
        assertNull(app.getAssignedPort());
    }

    @Test
    @DisplayName("배포 대기열이 가득 차면 즉시 FAILED")
    void queueFull() {
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        Executor rejecting = task -> {
            throw new RejectedExecutionException();
        };

        deployService(rejecting).onDeployRequested(new DeployRequestedEvent(APP_ID, DeployTrigger.MANUAL));

        assertEquals(AppStatus.FAILED, app.getStatus());
        assertEquals(ErrorCode.DEPLOY_QUEUE_FULL.getMessage(), app.getFailureReason());
        verify(sourceFetcher, never()).fetch(anyString());
        verify(recorder).fail(DEPLOYMENT_ID, ErrorCode.DEPLOY_QUEUE_FULL.getMessage());
    }

    @Test
    @DisplayName("재배포: 빌드가 끝난 뒤 이전 컨테이너를 지우고 새 컨테이너를 같은 포트로 띄운다")
    void redeployReplacesOldContainer() throws IOException {
        app.startBuilding();
        app.run("old-container");
        app.redeploy(null);
        givenDockerfile();
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenReturn(source());
        when(containerRuntime.run(eq(APP_ID), anyString(), anyString(), anyInt(), anyInt())).thenReturn("new-container");

        deploy();

        InOrder order = inOrder(imageBuilder, containerRuntime);
        order.verify(imageBuilder).build(any(), anyString());
        order.verify(containerRuntime).remove("old-container");
        order.verify(containerRuntime).run(eq(APP_ID), anyString(), anyString(), eq(10001), anyInt());
        assertEquals(AppStatus.RUNNING, app.getStatus());
        assertEquals("new-container", app.getContainerId());
    }

    @Test
    @DisplayName("재배포 중 빌드가 실패하면 이전 컨테이너는 건드리지 않는다 (정리는 재조정 루프 담당)")
    void redeployBuildFailureKeepsOldContainerUntouched() throws IOException {
        app.startBuilding();
        app.run("old-container");
        app.redeploy(null);
        givenDockerfile();
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenReturn(source());
        when(imageBuilder.build(any(), anyString()))
                .thenThrow(new CustomException(ErrorCode.IMAGE_BUILD_FAILED, "npm ERR!", new RuntimeException()));

        deploy();

        assertEquals(AppStatus.FAILED, app.getStatus());
        verify(containerRuntime, never()).remove(anyString());
    }

    @Test
    @DisplayName("배포 이력을 남기지 못해도(기록 ID null) 배포는 정상 진행된다")
    void deploysEvenWhenHistoryUnavailable() throws IOException {
        givenDockerfile();
        when(recorder.start(eq(APP_ID), any())).thenReturn(null);
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenReturn(source());
        when(containerRuntime.run(eq(APP_ID), anyString(), anyString(), anyInt(), anyInt())).thenReturn("c-1");

        deploy();

        assertEquals(AppStatus.RUNNING, app.getStatus());
    }
}
