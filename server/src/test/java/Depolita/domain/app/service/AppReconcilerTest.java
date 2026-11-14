package Depolita.domain.app.service;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.app.service.AppReconciler;
import Depolita.infra.ContainerManager;
import Depolita.infra.ContainerManager.ContainerInspection;
import Depolita.infra.ContainerManager.ContainerStatus;
import Depolita.infra.ContainerManager.ManagedContainer;
import Depolita.infra.ImageBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppReconcilerTest {
    private final AppRepository appRepository = mock(AppRepository.class);
    private final ContainerManager containerManager = mock(ContainerManager.class);
    private final ImageBuilder imageBuilder = mock(ImageBuilder.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    private AppReconciler reconciler;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(appRepository.findAllByStatusIn(anyCollection())).thenReturn(List.of());
        when(appRepository.findAll()).thenReturn(List.of());
        when(containerManager.listManaged()).thenReturn(List.of());
        reconciler = new AppReconciler(appRepository, containerManager, imageBuilder, txManager);
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
        when(containerManager.inspect("c-1")).thenReturn(new ContainerInspection(ContainerStatus.MISSING, false, 0));

        reconciler.reconcile();

        assertEquals(AppStatus.FAILED, app.getStatus());
        assertNull(app.getAssignedPort());
        verify(containerManager).remove("c-1");
    }

    @Test
    @DisplayName("검사하는 사이 사용자가 중지했다면 FAILED로 덮어쓰지 않는다")
    void userStoppedInBetween() {
        App snapshot = runningApp(1L, "c-1");
        App latest = runningApp(1L, "c-1");
        latest.stop(); // 재조정 루프가 다시 읽었을 때는 이미 STOPPED
        when(appRepository.findAllByStatusIn(anyCollection())).thenReturn(List.of(snapshot));
        when(appRepository.findById(1L)).thenReturn(Optional.of(latest));
        when(containerManager.inspect("c-1")).thenReturn(new ContainerInspection(ContainerStatus.EXITED, false, 0));

        reconciler.reconcile();

        assertEquals(AppStatus.STOPPED, latest.getStatus());
        verify(containerManager, never()).remove("c-1");
    }

    @Test
    @DisplayName("DB에서 삭제된 앱의 컨테이너 → 컨테이너와 이미지 삭제")
    void orphanOfDeletedApp() {
        App other = runningApp(5L, "c-5");
        when(appRepository.findAll()).thenReturn(List.of(other));
        when(containerManager.inspect("c-5")).thenReturn(new ContainerInspection(ContainerStatus.RUNNING, false, 0));
        when(containerManager.listManaged()).thenReturn(List.of(
                new ManagedContainer("c-5", 5L),
                new ManagedContainer("c-3", 3L) // id 3은 DB에 없음
        ));

        reconciler.reconcile();

        verify(containerManager).remove("c-3");
        verify(imageBuilder).remove(DeployService.imageTag(3L));
        verify(containerManager, never()).remove("c-5");
    }

    @Test
    @DisplayName("배포 중인 앱의 컨테이너는 건드리지 않는다")
    void inProgressAppIsSkipped() {
        App building = App.create("building", "https://github.com/1ub7/demo.git", 8080, 10001);
        ReflectionTestUtils.setField(building, "id", 2L);
        building.startBuilding();
        when(appRepository.findAll()).thenReturn(List.of(building));
        when(containerManager.listManaged()).thenReturn(List.of(new ManagedContainer("c-2", 2L)));

        reconciler.reconcile();

        verify(containerManager, never()).remove(anyString());
    }

    @Test
    @DisplayName("STOPPED인데 컨테이너가 돌고 있음 → 다시 중지")
    void stoppedButStillRunning() {
        App app = runningApp(1L, "c-1");
        app.stop();
        when(appRepository.findAllByStatusIn(anyCollection())).thenReturn(List.of(app));
        when(appRepository.findAll()).thenReturn(List.of(app));
        when(containerManager.inspect("c-1")).thenReturn(new ContainerInspection(ContainerStatus.RUNNING, false, 0));
        when(containerManager.listManaged()).thenReturn(List.of(new ManagedContainer("c-1", 1L)));

        reconciler.reconcile();

        verify(containerManager).stop("c-1");
        verify(containerManager, never()).remove("c-1");
    }
}
