package Depolita.domain.deployment.service;

import Depolita.domain.app.exception.AppNotFoundException;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.deployment.dto.DeploymentResponse;
import Depolita.domain.deployment.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeploymentQueryService {
    private final AppRepository appRepository;
    private final DeploymentRepository deploymentRepository;

    public List<DeploymentResponse> getRecent(Long appId) {
        if (!appRepository.existsById(appId)) {
            throw new AppNotFoundException();
        }
        return deploymentRepository.findTop20ByAppIdOrderByStartedAtDescIdDesc(appId).stream()
                .map(DeploymentResponse::from)
                .toList();
    }
}
