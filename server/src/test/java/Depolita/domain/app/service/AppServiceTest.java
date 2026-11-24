package Depolita.domain.app.service;

import Depolita.domain.account.config.AllowedAccountProperties;
import Depolita.domain.account.service.AccountValidator;
import Depolita.domain.app.dto.AppCreateRequest;
import Depolita.domain.app.dto.AppResponse;
import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.exception.AppLimitExceededException;
import Depolita.domain.app.exception.DuplicateAppNameException;
import Depolita.domain.app.exception.InvalidAppStatusException;
import Depolita.domain.app.port.ContainerRuntime.ContainerStatus;
import Depolita.domain.app.port.ImageBuilder;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.deployment.entity.DeployTrigger;
import Depolita.global.config.DepolitaProperties;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import Depolita.support.FakeContainerRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppServiceTest {
    private static final AppCreateRequest REQUEST =
            new AppCreateRequest("my-app", "https://github.com/1ub7/demo", 8080);

    private final AppRepository appRepository = mock(AppRepository.class);
    private final PortAllocator portAllocator = mock(PortAllocator.class);
    private final ImageBuilder imageBuilder = mock(ImageBuilder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final FakeContainerRuntime containerRuntime = new FakeContainerRuntime();

    private AppService appService;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(portAllocator.allocate(anySet())).thenReturn(10001);
        doAnswer(inv -> withId(inv.getArgument(0), 1L)).when(appRepository).save(any(App.class));

        appService = new AppService(
                appRepository,
                new AccountValidator(new AllowedAccountProperties(Set.of("1ub7"))),
                portAllocator,
                containerRuntime,
                imageBuilder,
                eventPublisher,
                new TransactionTemplate(txManager),
                new DepolitaProperties("test-key", "localhost"));
    }

    private static App withId(App app, Long id) {
        ReflectionTestUtils.setField(app, "id", id);
        return app;
    }

    private App givenApp(Long id, AppStatus status, String containerId) {
        App app = withId(App.create("app-" + id, "https://github.com/1ub7/demo.git", 8080, 10001), id);
        if (status != AppStatus.CLONING) app.startBuilding();
        if (status == AppStatus.RUNNING) app.run(containerId);
        when(appRepository.findById(id)).thenReturn(Optional.of(app));
        return app;
    }

    // ---------- create ----------

    @Test
    @DisplayName("이미 있는 이름이면 DuplicateAppNameException, 저장도 배포 이벤트도 없다")
    void duplicateName() {
        when(appRepository.existsByName("my-app")).thenReturn(true);

        assertThrows(DuplicateAppNameException.class, () -> appService.create(REQUEST));

        verify(appRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("실행 중인 앱이 최대 개수면 AppLimitExceededException, 저장도 배포 이벤트도 없다")
    void appLimitExceeded() {
        when(appRepository.countByStatusIn(anySet())).thenReturn((long) AppService.MAX_ACTIVE_APPS);

        assertThrows(AppLimitExceededException.class, () -> appService.create(REQUEST));

        verify(appRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("최대 개수보다 하나 적으면 생성된다")
    void belowAppLimit() {
        when(appRepository.countByStatusIn(anySet())).thenReturn((long) AppService.MAX_ACTIVE_APPS - 1);

        AppResponse response = appService.create(REQUEST);

        assertEquals(AppStatus.CLONING, response.status());
        verify(eventPublisher).publishEvent(new DeployRequestedEvent(1L, DeployTrigger.INITIAL));
    }

    @Test
    @DisplayName("포트 충돌(UNIQUE 위반)이 나면 다시 시도해서 성공한다")
    void retriesOnPortConflict() {
        doThrow(new DataIntegrityViolationException("uk_apps_assigned_port"))
                .doAnswer(inv -> withId(inv.getArgument(0), 1L))
                .when(appRepository).save(any(App.class));

        AppResponse response = appService.create(REQUEST);

        assertEquals(AppStatus.CLONING, response.status());
        verify(appRepository, times(2)).save(any());
        verify(eventPublisher, times(1)).publishEvent(any(DeployRequestedEvent.class));
    }

    @Test
    @DisplayName("3번 모두 포트가 충돌하면 PORT_ALLOCATION_CONFLICT")
    void givesUpAfterThreeConflicts() {
        doThrow(new DataIntegrityViolationException("uk_apps_assigned_port"))
                .when(appRepository).save(any(App.class));

        CustomException e = assertThrows(CustomException.class, () -> appService.create(REQUEST));

        assertEquals(ErrorCode.PORT_ALLOCATION_CONFLICT, e.getErrorCode());
        verify(appRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("저장하는 사이 같은 이름이 먼저 들어왔다면(UNIQUE 위반) 재시도하지 않고 이름 중복으로 응답")
    void nameTakenConcurrently() {
        when(appRepository.existsByName("my-app")).thenReturn(false, true); // 저장 전엔 없었는데, 실패 후 보니 있음
        doThrow(new DataIntegrityViolationException("uk_apps_name"))
                .when(appRepository).save(any(App.class));

        assertThrows(DuplicateAppNameException.class, () -> appService.create(REQUEST));

        verify(appRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("실제로 떠 있는 컨테이너의 포트를 PortAllocator에 넘긴다")
    void passesHostPortsToAllocator() {
        containerRuntime.givenRunning(99L, 10001);

        appService.create(REQUEST);

        verify(portAllocator).allocate(Set.of(10001));
    }

    // ---------- stop ----------

    @Test
    @DisplayName("RUNNING 앱을 중지하면 STOPPED, 포트 반납, 컨테이너도 실제로 멈춘다")
    void stopRunningApp() {
        String containerId = containerRuntime.givenRunning(1L, 10001);
        App app = givenApp(1L, AppStatus.RUNNING, containerId);

        AppResponse response = appService.stop(1L);

        assertEquals(AppStatus.STOPPED, response.status());
        assertNull(app.getAssignedPort());
        assertEquals(ContainerStatus.EXITED, containerRuntime.statusOf(containerId));
    }

    @Test
    @DisplayName("RUNNING이 아닌 앱(배포 중)은 중지할 수 없다")
    void cannotStopBuildingApp() {
        App app = givenApp(1L, AppStatus.BUILDING, null);

        assertThrows(InvalidAppStatusException.class, () -> appService.stop(1L));

        assertEquals(AppStatus.BUILDING, app.getStatus());
    }

    @Test
    @DisplayName("컨테이너가 외부에서 지워졌어도 중지는 성공한다")
    void stopWhenContainerAlreadyGone() {
        givenApp(1L, AppStatus.RUNNING, "already-removed");

        assertEquals(AppStatus.STOPPED, appService.stop(1L).status());
    }

    // ---------- redeploy ----------

    private static App newStoppedApp(Long id) {
        App app = withId(App.create("app-" + id, "https://github.com/1ub7/demo.git", 8080, 10001), id);
        app.startBuilding();
        app.run("c-" + id);
        app.stop();
        return app;
    }

    private App givenStoppedApp(Long id) {
        App app = newStoppedApp(id);
        when(appRepository.findById(id)).thenReturn(Optional.of(app));
        return app;
    }

    @Test
    @DisplayName("실행 중인 앱을 재배포하면 같은 포트를 유지하고 포트를 새로 고르지 않는다")
    void redeployRunningKeepsPort() {
        givenApp(1L, AppStatus.RUNNING, "c-1");

        AppResponse response = appService.redeploy(1L, DeployTrigger.MANUAL);

        assertEquals(AppStatus.CLONING, response.status());
        assertEquals(10001, response.assignedPort());
        verify(portAllocator, never()).allocate(anySet());
        verify(eventPublisher).publishEvent(new DeployRequestedEvent(1L, DeployTrigger.MANUAL));
    }

    @Test
    @DisplayName("중지한 앱을 재배포하면 포트를 새로 할당받는다")
    void redeployStoppedAllocatesPort() {
        App app = givenStoppedApp(1L);
        when(portAllocator.allocate(anySet())).thenReturn(10002);

        appService.redeploy(1L, DeployTrigger.MANUAL);

        assertEquals(10002, app.getAssignedPort());
        verify(portAllocator).allocate(anySet());
    }

    @Test
    @DisplayName("배포 진행 중인 앱은 재배포할 수 없다")
    void cannotRedeployInProgress() {
        givenApp(1L, AppStatus.BUILDING, null);

        assertThrows(InvalidAppStatusException.class, () -> appService.redeploy(1L, DeployTrigger.MANUAL));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("중지한 앱을 재배포할 때 활성 앱이 최대 개수면 AppLimitExceededException")
    void redeployStoppedRespectsAppLimit() {
        givenStoppedApp(1L);
        when(appRepository.countByStatusIn(anySet())).thenReturn((long) AppService.MAX_ACTIVE_APPS);

        assertThrows(AppLimitExceededException.class, () -> appService.redeploy(1L, DeployTrigger.MANUAL));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("실행 중인 앱의 재배포는 활성 앱 수가 늘지 않으므로 개수 제한에 걸리지 않는다")
    void redeployRunningIgnoresAppLimit() {
        givenApp(1L, AppStatus.RUNNING, "c-1");
        when(appRepository.countByStatusIn(anySet())).thenReturn((long) AppService.MAX_ACTIVE_APPS);

        AppResponse response = appService.redeploy(1L, DeployTrigger.MANUAL);

        assertEquals(AppStatus.CLONING, response.status());
    }

    @Test
    @DisplayName("재배포 중 포트가 충돌하면 다시 시도한다")
    void redeployRetriesOnPortConflict() {
        // 실제로는 롤백 후 새 트랜잭션에서 엔티티를 다시 읽으므로, 시도마다 새 객체를 돌려준다
        App firstTry = newStoppedApp(1L);
        App secondTry = newStoppedApp(1L);
        when(appRepository.findById(1L)).thenReturn(Optional.of(firstTry), Optional.of(secondTry));
        when(appRepository.saveAndFlush(any(App.class)))
                .thenThrow(new DataIntegrityViolationException("uk_apps_assigned_port"))
                .thenReturn(secondTry);

        appService.redeploy(1L, DeployTrigger.MANUAL);

        verify(appRepository, times(2)).saveAndFlush(any(App.class));
        assertTrue(secondTry.isInProgress());
    }

    // ---------- delete ----------

    @Test
    @DisplayName("앱을 삭제하면 컨테이너와 이미지까지 지운다")
    void deleteRemovesContainerAndImage() {
        String containerId = containerRuntime.givenRunning(1L, 10001);
        App app = givenApp(1L, AppStatus.RUNNING, containerId);

        appService.delete(1L);

        verify(appRepository).delete(app);
        assertFalse(containerRuntime.exists(containerId));
        verify(imageBuilder).remove(DeployService.imageTag(1L));
    }

    @Test
    @DisplayName("배포 중인 앱은 삭제할 수 없다")
    void cannotDeleteBuildingApp() {
        givenApp(1L, AppStatus.BUILDING, null);

        assertThrows(InvalidAppStatusException.class, () -> appService.delete(1L));

        verify(appRepository, never()).delete(any());
    }
}
