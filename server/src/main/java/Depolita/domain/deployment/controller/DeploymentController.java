package Depolita.domain.deployment.controller;

import Depolita.domain.deployment.dto.DeploymentResponse;
import Depolita.domain.deployment.service.DeploymentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/apps/{appId}/deployments")
@RequiredArgsConstructor
public class DeploymentController {
    private final DeploymentQueryService deploymentQueryService;

    @GetMapping
    public List<DeploymentResponse> getDeployments(@PathVariable Long appId) {
        return deploymentQueryService.getRecent(appId);
    }
}
