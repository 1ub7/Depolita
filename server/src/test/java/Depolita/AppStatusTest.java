package Depolita;

import Depolita.domain.app.entity.AppStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppStatusTest {

    @Test
    @DisplayName("허용된 전이")
    void allowedTransitions() {
        assertTrue(AppStatus.CLONING.canTransitionTo(AppStatus.BUILDING));
        assertTrue(AppStatus.BUILDING.canTransitionTo(AppStatus.RUNNING));
        assertTrue(AppStatus.RUNNING.canTransitionTo(AppStatus.STOPPED));
        assertTrue(AppStatus.CLONING.canTransitionTo(AppStatus.FAILED));
        assertTrue(AppStatus.BUILDING.canTransitionTo(AppStatus.FAILED));
        assertTrue(AppStatus.RUNNING.canTransitionTo(AppStatus.FAILED));
    }

    @Test
    @DisplayName("실행 중·중지·실패 상태에서는 재배포(CLONING)할 수 있다")
    void redeployTransitions() {
        assertTrue(AppStatus.RUNNING.canTransitionTo(AppStatus.CLONING));
        assertTrue(AppStatus.STOPPED.canTransitionTo(AppStatus.CLONING));
        assertTrue(AppStatus.FAILED.canTransitionTo(AppStatus.CLONING));
    }

    @Test
    @DisplayName("단계를 건너뛰거나 되돌아갈 수 없다")
    void forbiddenTransitions() {
        assertFalse(AppStatus.CLONING.canTransitionTo(AppStatus.RUNNING));
        assertFalse(AppStatus.RUNNING.canTransitionTo(AppStatus.BUILDING));
        assertFalse(AppStatus.BUILDING.canTransitionTo(AppStatus.CLONING));
        assertFalse(AppStatus.CLONING.canTransitionTo(AppStatus.CLONING));
    }

    @ParameterizedTest
    @EnumSource(value = AppStatus.class, names = "CLONING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("STOPPED, FAILED는 재배포(CLONING)로만 나갈 수 있다")
    void stoppedAndFailedOnlyRedeploy(AppStatus next) {
        assertFalse(AppStatus.STOPPED.canTransitionTo(next));
        assertFalse(AppStatus.FAILED.canTransitionTo(next));
    }
}
