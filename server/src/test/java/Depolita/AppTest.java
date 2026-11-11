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
}