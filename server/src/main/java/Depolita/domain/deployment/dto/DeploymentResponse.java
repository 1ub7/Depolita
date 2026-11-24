package Depolita.domain.deployment.dto;

import Depolita.domain.deployment.entity.DeployTrigger;
import Depolita.domain.deployment.entity.Deployment;
import Depolita.domain.deployment.entity.DeploymentStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record DeploymentResponse(
        Long id,
        DeployTrigger trigger,
        String commitSha,
        DeploymentStatus status,
        String failureReason,
        Long cloneMs,
        Long buildMs,
        Instant startedAt,
        Instant finishedAt
) {
    public static DeploymentResponse from(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getTrigger(),
                deployment.getCommitSha(),
                deployment.getStatus(),
                deployment.getFailureReason(),
                deployment.getCloneMs(),
                deployment.getBuildMs(),
                toInstant(deployment.getStartedAt()),
                toInstant(deployment.getFinishedAt())
        );
    }

    private static Instant toInstant(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.MILLIS);
    }
}
