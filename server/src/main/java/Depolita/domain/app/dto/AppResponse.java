package Depolita.domain.app.dto;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record AppResponse(
        Long id,
        String name,
        String repositoryUrl,
        Integer internalPort,
        Integer assignedPort,
        String url,
        AppStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static AppResponse from(App app, String publicHost) {
        return new AppResponse(
                app.getId(),
                app.getName(),
                app.getRepositoryUrl(),
                app.getInternalPort(),
                app.getAssignedPort(),
                accessUrl(app, publicHost),
                app.getStatus(),
                app.getFailureReason(),
                toInstant(app.getCreatedAt()),
                toInstant(app.getUpdatedAt())
        );
    }

    private static String accessUrl(App app, String publicHost) {
        if (app.getStatus() != AppStatus.RUNNING || app.getAssignedPort() == null) {
            return null;
        }
        return "http://" + publicHost + ":" + app.getAssignedPort();
    }

    private static Instant toInstant(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.MILLIS);
    }
}