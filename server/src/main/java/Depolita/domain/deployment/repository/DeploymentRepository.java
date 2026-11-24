package Depolita.domain.deployment.repository;

import Depolita.domain.deployment.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findTop20ByAppIdOrderByStartedAtDescIdDesc(Long appId);
}
