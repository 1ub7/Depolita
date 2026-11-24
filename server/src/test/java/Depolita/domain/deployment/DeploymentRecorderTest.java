package Depolita.domain.deployment;

import Depolita.domain.deployment.entity.DeployTrigger;
import Depolita.domain.deployment.entity.Deployment;
import Depolita.domain.deployment.entity.DeploymentStatus;
import Depolita.domain.deployment.repository.DeploymentRepository;
import Depolita.domain.deployment.service.DeploymentRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentRecorderTest {
    private final DeploymentRepository repository = mock(DeploymentRepository.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private DeploymentRecorder recorder;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        recorder = new DeploymentRecorder(repository, txManager);
    }

    @Test
    @DisplayName("시작 기록을 저장하고 ID를 돌려준다")
    void start() {
        when(repository.save(any(Deployment.class))).thenAnswer(inv -> {
            Deployment d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 7L);
            return d;
        });

        assertEquals(7L, recorder.start(1L, DeployTrigger.MANUAL));
    }

    @Test
    @DisplayName("DB 저장이 실패해도 예외를 던지지 않고 null을 돌려준다 (배포는 계속돼야 함)")
    void startFailureIsSwallowed() {
        when(repository.save(any(Deployment.class))).thenThrow(new RuntimeException("DB down"));

        assertNull(recorder.start(1L, DeployTrigger.MANUAL));
    }

    @Test
    @DisplayName("기록 ID가 null이면 아무것도 하지 않는다")
    void nullIdIsIgnored() {
        recorder.succeed(null);
        recorder.fail(null, "x");

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("갱신 중 예외가 나도 밖으로 던지지 않는다")
    void updateFailureIsSwallowed() {
        when(repository.findById(7L)).thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() -> recorder.succeed(7L));
    }

    @Test
    @DisplayName("성공을 기록한다")
    void succeed() {
        Deployment d = Deployment.start(1L, DeployTrigger.MANUAL, java.time.LocalDateTime.now());
        when(repository.findById(7L)).thenReturn(Optional.of(d));

        recorder.succeed(7L);

        assertEquals(DeploymentStatus.SUCCEEDED, d.getStatus());
    }
}
