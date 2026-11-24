package Depolita.domain.deployment.repository;

import Depolita.domain.deployment.entity.Deployment;
import Depolita.domain.deployment.entity.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findTop20ByAppIdOrderByStartedAtDescIdDesc(Long appId);
    List<Deployment> findAllByStatusAndStartedAtBefore(DeploymentStatus status, LocalDateTime time);
}
