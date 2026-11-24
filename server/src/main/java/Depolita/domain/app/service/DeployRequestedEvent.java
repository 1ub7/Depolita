package Depolita.domain.app.service;

import Depolita.domain.deployment.entity.DeployTrigger;

public record DeployRequestedEvent(Long appId, DeployTrigger trigger) {
}
