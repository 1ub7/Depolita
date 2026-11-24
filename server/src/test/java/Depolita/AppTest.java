package Depolita;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.exception.InvalidAppStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    private App newApp() {
        return App.create("my-app", "https://github.com/1ub7/demo.git", 8080, 10001);
    }

    @Test
    @DisplayName("생성 직후 상태는 CLONING이고 포트를 가진다")
    void create() {
        App app = newApp();

        assertEquals(AppStatus.CLONING, app.getStatus());
        assertEquals(10001, app.getAssignedPort());
        assertTrue(app.isInProgress());
    }

    @Test
    @DisplayName("정상 흐름: CLONING → BUILDING → RUNNING")
    void happyPath() {
        App app = newApp();

        app.startBuilding();
        app.run("container-1");

        assertEquals(AppStatus.RUNNING, app.getStatus());
        assertEquals("container-1", app.getContainerId());
        assertEquals(10001, app.getAssignedPort());
    }

    @Test
    @DisplayName("실패하면 원인을 저장하고 포트를 반납한다")
    void failReleasesPort() {
        App app = newApp();

        app.fail("빌드 실패");

        assertEquals(AppStatus.FAILED, app.getStatus());
        assertEquals("빌드 실패", app.getFailureReason());
        assertNull(app.getAssignedPort());
    }

    @Test
    @DisplayName("중지하면 포트를 반납한다")
    void stopReleasesPort() {
        App app = newApp();
        app.startBuilding();
        app.run("container-1");

        app.stop();

        assertEquals(AppStatus.STOPPED, app.getStatus());
        assertNull(app.getAssignedPort());
    }

    @Test
    @DisplayName("BUILDING을 건너뛰고 RUNNING으로 갈 수 없다")
    void cannotSkipBuilding() {
        App app = newApp();

        assertThrows(InvalidAppStatusException.class, () -> app.run("container-1"));
        assertEquals(AppStatus.CLONING, app.getStatus());
    }

    @Test
    @DisplayName("RUNNING이 아니면 중지할 수 없다")
    void cannotStopWhenNotRunning() {
        App app = newApp();

        assertThrows(InvalidAppStatusException.class, app::stop);
    }

    @Test
    @DisplayName("실패 원인이 너무 길면 최대 길이로 자른다")
    void truncateFailureReason() {
        App app = newApp();

        app.fail("x".repeat(App.FAILURE_REASON_MAX_LENGTH + 500));

        assertEquals(App.FAILURE_REASON_MAX_LENGTH, app.getFailureReason().length());
    }

    // ---------- redeploy ----------

    private App runningApp() {
        App app = newApp();
        app.startBuilding();
        app.run("container-1");
        return app;
    }

    @Test
    @DisplayName("실행 중인 앱을 재배포하면 CLONING이 되고 포트와 컨테이너 ID는 유지된다")
    void redeployRunningKeepsPort() {
        App app = runningApp();

        app.redeploy(null);

        assertEquals(AppStatus.CLONING, app.getStatus());
        assertEquals(10001, app.getAssignedPort());
        assertEquals("container-1", app.getContainerId());
    }

    @Test
    @DisplayName("중지한 앱을 재배포하면 새 포트를 받는다")
    void redeployStoppedGetsNewPort() {
        App app = runningApp();
        app.stop();

        app.redeploy(10002);

        assertEquals(AppStatus.CLONING, app.getStatus());
        assertEquals(10002, app.getAssignedPort());
    }

    @Test
    @DisplayName("실패한 앱을 재배포하면 실패 원인을 지운다")
    void redeployFailedClearsReason() {
        App app = newApp();
        app.fail("빌드 실패");

        app.redeploy(10002);

        assertNull(app.getFailureReason());
        assertEquals(10002, app.getAssignedPort());
    }

    @Test
    @DisplayName("포트를 반납한 앱인데 새 포트가 없으면 재배포할 수 없다")
    void redeployWithoutPortFails() {
        App app = newApp();
        app.fail("빌드 실패");

        assertThrows(NullPointerException.class, () -> app.redeploy(null));
    }

    @Test
    @DisplayName("배포 진행 중에는 재배포할 수 없다")
    void cannotRedeployInProgress() {
        App app = newApp();

        assertThrows(InvalidAppStatusException.class, () -> app.redeploy(null));
        assertEquals(AppStatus.CLONING, app.getStatus());
    }
}
