package Depolita.domain.deployment.service;

import Depolita.domain.deployment.entity.DeployTrigger;
import Depolita.domain.deployment.entity.Deployment;
import Depolita.domain.deployment.entity.DeploymentStatus;
import Depolita.domain.deployment.repository.DeploymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
public class DeploymentRecorder {
    static final String INTERRUPTED_REASON = "서버 재시작으로 배포가 중단되었습니다.";

    private final DeploymentRepository deploymentRepository;
    private final TransactionTemplate newTransaction;
    // 이 시각 이전에 시작해서 아직 IN_PROGRESS 인 기록은 이전 서버 프로세스가 남긴 것이다
    private final LocalDateTime bootedAt = LocalDateTime.now();

    public DeploymentRecorder(DeploymentRepository deploymentRepository,
                              PlatformTransactionManager transactionManager) {
        this.deploymentRepository = deploymentRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return 기록 ID. 기록에 실패하면 null 이고, 이후 호출은 모두 무시된다 */
    public Long start(Long appId, DeployTrigger trigger) {
        try {
            return newTransaction.execute(status ->
                    deploymentRepository.save(Deployment.start(appId, trigger, LocalDateTime.now())).getId());
        } catch (Exception e) {
            log.warn("[deployment] appId={} 이력 시작 기록 실패", appId, e);
            return null;
        }
    }

    public void recordClone(Long deploymentId, String commitSha, long elapsedMs) {
        update(deploymentId, d -> d.recordClone(commitSha, elapsedMs));
    }

    public void recordBuild(Long deploymentId, long elapsedMs) {
        update(deploymentId, d -> d.recordBuild(elapsedMs));
    }

    public void succeed(Long deploymentId) {
        update(deploymentId, d -> d.succeed(LocalDateTime.now()));
    }

    public void fail(Long deploymentId, String reason) {
        update(deploymentId, d -> d.fail(reason, LocalDateTime.now()));
    }

    /**
     * 서버가 배포 도중 재시작되면 그 배포 이력은 끝나지 않은 채(IN_PROGRESS) 남는다.
     * 앱 상태는 AppReconciler.failInterruptedDeployments() 가 FAILED 로 바꾸므로, 이력도 같이 FAILED 로 맞춘다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterrupted() {
        try {
            newTransaction.executeWithoutResult(status -> {
                List<Deployment> stuck = deploymentRepository.findAllByStatusAndStartedAtBefore(
                        DeploymentStatus.IN_PROGRESS, bootedAt);
                LocalDateTime now = LocalDateTime.now();
                stuck.forEach(d -> d.fail(INTERRUPTED_REASON, now));
                if (!stuck.isEmpty()) {
                    log.warn("[deployment] 중단된 배포 이력 {}건을 FAILED로 변경", stuck.size());
                }
            });
        } catch (Exception e) {
            log.warn("[deployment] 중단된 배포 이력 정리 실패", e);
        }
    }

    private void update(Long deploymentId, Consumer<Deployment> change) {
        if (deploymentId == null) {
            return;
        }
        try {
            newTransaction.executeWithoutResult(status ->
                    deploymentRepository.findById(deploymentId).ifPresent(change));
        } catch (Exception e) {
            log.warn("[deployment] id={} 이력 갱신 실패", deploymentId, e);
        }
    }
}
