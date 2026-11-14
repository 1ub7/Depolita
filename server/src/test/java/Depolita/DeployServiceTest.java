package Depolita;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.app.service.AppCreatedEvent;
import Depolita.domain.app.service.DeployService;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import Depolita.domain.app.port.ContainerRuntime;
import Depolita.domain.app.port.ImageBuilder;
import Depolita.domain.app.port.SourceFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeployServiceTest {
    private static final Long APP_ID = 1L;

    @TempDir
    Path workDir;

    private final AppRepository appRepository = mock(AppRepository.class);
    private final SourceFetcher sourceFetcher = mock(SourceFetcher.class);
    private final ImageBuilder imageBuilder = mock(ImageBuilder.class);
    private final ContainerRuntime containerRuntime = mock(ContainerRuntime.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    private App app;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        app = App.create("my-app", "https://github.com/1ub7/demo.git", 8080, 10001);
    }

    private DeployService deployService(Executor executor) {
        return new DeployService(appRepository, sourceFetcher, imageBuilder, containerRuntime, executor, txManager);
    }

    private void deploy() {
        deployService(Runnable::run).onAppCreated(new AppCreatedEvent(APP_ID));
    }

    private void givenDockerfile() throws IOException {
        Files.writeString(workDir.resolve("Dockerfile"), "FROM alpine");
    }

    @Test
    @DisplayName("정상 흐름: RUNNING이 되고 컨테이너 ID가 저장된다")
    void success() throws IOException {
        givenDockerfile();
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenReturn(workDir);
        when(containerRuntime.run(eq(APP_ID), anyString(), anyString(), anyInt(), anyInt())).thenReturn("c-1");

        deploy();

        assertEquals(AppStatus.RUNNING, app.getStatus());
        assertEquals("c-1", app.getContainerId());
        assertEquals(10001, app.getAssignedPort());
        verify(sourceFetcher).cleanup(workDir);
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
    }

    @Test
    @DisplayName("Dockerfile 없음 → FAILED, 복제한 폴더는 정리")
    void dockerfileMissing() {
        when(appRepository.findById(APP_ID)).thenReturn(Optional.of(app));
        when(sourceFetcher.fetch(anyString())).thenReturn(workDir); // 빈 폴더

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
        when(sourceFetcher.fetch(anyString())).thenReturn(workDir);
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
        when(sourceFetcher.fetch(anyString())).thenReturn(workDir);
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

        deployService(rejecting).onAppCreated(new AppCreatedEvent(APP_ID));

        assertEquals(AppStatus.FAILED, app.getStatus());
        assertEquals(ErrorCode.DEPLOY_QUEUE_FULL.getMessage(), app.getFailureReason());
        verify(sourceFetcher, never()).fetch(anyString());
    }
}