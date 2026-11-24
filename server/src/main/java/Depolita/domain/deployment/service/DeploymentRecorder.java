package Depolita.domain.deployment.service;

import Depolita.domain.deployment.entity.DeployTrigger;
import Depolita.domain.deployment.entity.Deployment;
import Depolita.domain.deployment.repository.DeploymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Slf4j
@Service
public class DeploymentRecorder {
    private final DeploymentRepository deploymentRepository;
    private final TransactionTemplate newTransaction;

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
