package Depolita.domain.app.service;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.app.port.ContainerRuntime;
import Depolita.domain.app.port.ContainerRuntime.ContainerInspection;
import Depolita.domain.app.port.ContainerRuntime.ContainerStatus;
import Depolita.domain.app.port.ContainerRuntime.ManagedContainer;
import Depolita.domain.app.port.ImageBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppReconcilerTest {
    private final AppRepository appRepository = mock(AppRepository.class);
    private final ContainerRuntime containerRuntime = mock(ContainerRuntime.class);
    private final ImageBuilder imageBuilder = mock(ImageBuilder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    private AppReconciler reconciler;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(appRepository.findAllByStatusIn(anyCollection())).thenReturn(List.of());
        when(appRepository.findAll()).thenReturn(List.of());
        when(containerRuntime.listManaged()).thenReturn(List.of());
        reconciler = new AppReconciler(appRepository, containerRuntime, imageBuilder, eventPublisher, txManager);
    }

    private App runningApp(Long id, String containerId) {
        App app = App.create("app-" + id, "https://github.com/1ub7/demo.git", 8080, 10000 + id.intValue());
        ReflectionTestUtils.setField(app, "id", id);
        app.startBuilding();
        app.run(containerId);
        return app;
    }

    @Test
    @DisplayName("실패 판정: 정상/재시작 몇 번은 OK, 종료·OOM·없음·CrashLoop은 실패")
    void failureReason() {
        assertNull(AppReconciler.failureReason(new ContainerInspection(ContainerStatus.RUNNING, false, 0)));
        assertNull(AppReconciler.failureReason(new ContainerInspection(ContainerStatus.RESTARTING, false, 1)));
        assertNotNull(AppReconciler.failureReason(
                new ContainerInspection(ContainerStatus.RESTARTING, false, AppReconciler.CRASH_LOOP_THRESHOLD)));
        assertNotNull(AppReconciler.failureReason(new ContainerInspection(ContainerStatus.EXITED, false, 0)));
        assertNotNull(AppReconciler.failureReason(new ContainerInspection(ContainerStatus.MISSING, false, 0)));
        String oom = AppReconciler.failureReason(new ContainerInspection(ContainerStatus.EXITED, true, 0));
        assertNotNull(oom);
    }

    @Test
    @DisplayName("DB는 RUNNING인데 컨테이너가 사라짐 → FAILED, 포트 반납")
    void runningButContainerMissing() {
        App app = runningApp(1L, "c-1");
        when(appRepository.findAllByStatusIn(anyCollection())).thenReturn(List.of(app));
        when(appRepository.findById(1L)).thenReturn(Optional.of(app));
        when(containerRuntime.inspect("c-1")).thenReturn(new ContainerInspection(ContainerStatus.MISSING, false, 0));

        reconciler.reconcile();

        assertEquals(AppStatus.FAILED, app.getStatus());
        assertNull(app.getAssignedPort());
        verify(containerRuntime).remove("c-1");
        verify(eventPublisher).publishEvent(any(AppCrashedEvent.class));
    }

    @Test
    @DisplayName("OOM으로 죽으면 앱 이름과 원인을 담아 장애 이벤트를 발행한다")
    void oomPublishesCrashEvent() {
        App app = runningApp(1L, "c-1");
        when(appRepository.findAllByStatusIn(anyCollection())).thenReturn(List.of(app));
        when(appRepository.findById(1L)).thenReturn(Optional.of(app));
        when(containerRuntime.inspect("c-1")).thenReturn(new ContainerInspection(ContainerStatus.EXITED, true, 0));

        reconciler.reconcile();

        ArgumentCaptor<AppCrashedEvent> event = ArgumentCaptor.forClass(AppCrashedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals("app-1", event.getValue().appName());
        assertEquals(app.getFailureReason(), event.getValue().reason());
    }

    @Test
    @DisplayName("검사하는 사이 사용자가 중지했다면 FAILED로 덮어쓰지 않는다")
    void userStoppedInBetween() {
        App snapshot = runningApp(1L, "c-1");
        App latest = runningApp(1L, "c-1");
        latest.stop();
        when(appRepository.findAllByStatusIn(anyCollection())).thenReturn(List.of(snapshot));
        when(appRepository.findById(1L)).thenReturn(Optional.of(latest));
        when(containerRuntime.inspect("c-1")).thenReturn(new ContainerInspection(ContainerStatus.EXITED, false, 0));

        reconciler.reconcile();

        assertEquals(AppStatus.STOPPED, latest.getStatus());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(containerRuntime, never()).remove("c-1");
    }

    @Test
    @DisplayName("DB에서 삭제된 앱의 컨테이너 → 컨테이너와 이미지 삭제")
    void orphanOfDeletedApp() {
        App other = runningApp(5L, "c-5");
        when(appRepository.findAll()).thenReturn(List.of(other));
        when(containerRuntime.inspect("c-5")).thenReturn(new ContainerInspection(ContainerStatus.RUNNING, false, 0));
        when(containerRuntime.listManaged()).thenReturn(List.of(
                new ManagedContainer("c-5", 5L),
                new ManagedContainer("c-3", 3L)
        ));

        reconciler.reconcile();

        verify(containerRuntime).remove("c-3");
        verify(imageBuilder).remove(DeployService.imageTag(3L));
        verify(containerRuntime, never()).remove("c-5");
    }

    @Test
    @DisplayName("배포 중인 앱의 컨테이너는 건드리지 않는다")
    void inProgressAppIsSkipped() {
        App building = App.create("building", "https://github.com/1ub7/demo.git", 8080, 10001);
        ReflectionTestUtils.setField(building, "id", 2L);
        building.startBuilding();
        when(appRepository.findAll()).thenReturn(List.of(building));
        when(containerRuntime.listManaged()).thenReturn(List.of(new ManagedContainer("c-2", 2L)));

        reconciler.reconcile();

        verify(containerRuntime, never()).remove(anyString());
    }

    @Test
    @DisplayName("STOPPED인데 컨테이너가 돌고 있음 → 다시 중지")
    void stoppedButStillRunning() {
        App app = runningApp(1L, "c-1");
        app.stop();
        when(appRepository.findAllByStatusIn(anyCollection())).thenReturn(List.of(app));
        when(appRepository.findAll()).thenReturn(List.of(app));
        when(containerRuntime.inspect("c-1")).thenReturn(new ContainerInspection(ContainerStatus.RUNNING, false, 0));
        when(containerRuntime.listManaged()).thenReturn(List.of(new ManagedContainer("c-1", 1L)));

        reconciler.reconcile();

        verify(containerRuntime).stop("c-1");
        verify(containerRuntime, never()).remove("c-1");
    }

    @Test
    @DisplayName("가장 최근에 만든 앱을 삭제해도 그 컨테이너는 고아로 정리된다")
    void orphanOfLatestDeletedApp() {
        App older = runningApp(1L, "c-1");
        when(appRepository.findAll()).thenReturn(List.of(older));
        when(containerRuntime.listManaged()).thenReturn(List.of(
                new ManagedContainer("c-1", 1L),
                new ManagedContainer("c-2", 2L)
        ));

        reconciler.reconcile();

        verify(containerRuntime).remove("c-2");
        verify(imageBuilder).remove(DeployService.imageTag(2L));
        verify(containerRuntime, never()).remove("c-1");
    }

    @Test
    @DisplayName("앱이 하나도 남아 있지 않아도 고아 컨테이너는 정리된다")
    void orphanWhenNoAppsLeft() {
        when(containerRuntime.listManaged()).thenReturn(List.of(new ManagedContainer("c-7", 7L)));

        reconciler.reconcile();

        verify(containerRuntime).remove("c-7");
    }

    @Test
    @DisplayName("컨테이너 목록을 DB보다 먼저 읽는다 (새로 만든 앱을 고아로 오판하지 않기 위해)")
    void listsContainersBeforeApps() {
        reconciler.reconcile();

        InOrder inOrder = inOrder(containerRuntime, appRepository);
        inOrder.verify(containerRuntime).listManaged();
        inOrder.verify(appRepository).findAll();
    }

    @Test
    @DisplayName("기동 시 Docker 네이티브 라이브러리 오류가 나도 서버 기동을 막지 않는다")
    void startupSurvivesLinkageError() {
        when(containerRuntime.listManaged()).thenThrow(new UnsatisfiedLinkError("docker native lib"));

        assertDoesNotThrow(reconciler::onStartup);
    }

    @Test
    @DisplayName("서버가 뜨기 전에 멈춘 배포만 FAILED로 바꾼다 (기동 직후 들어온 배포 요청은 건드리지 않음)")
    void failsOnlyDeploymentsInterruptedBeforeStartup() {
        LocalDateTime beforeCreate = LocalDateTime.now();
        AppReconciler fresh = new AppReconciler(appRepository, containerRuntime, imageBuilder, eventPublisher, txManager);
        LocalDateTime afterCreate = LocalDateTime.now();

        App interrupted = App.create("old", "https://github.com/1ub7/demo.git", 8080, 10001);
        when(appRepository.findAllByStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(List.of(interrupted));

        fresh.failInterruptedDeployments();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(appRepository).findAllByStatusInAndUpdatedAtBefore(anyCollection(), cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(beforeCreate));
        assertFalse(cutoff.getValue().isAfter(afterCreate));
        assertEquals(AppStatus.FAILED, interrupted.getStatus());
    }
}
