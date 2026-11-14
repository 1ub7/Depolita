package Depolita.domain.app.dto;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;

import java.time.LocalDateTime;

public record AppResponse(
        Long id,
        String name,
        String repositoryUrl,
        Integer internalPort,
        Integer assignedPort,
        String url,
        AppStatus status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }

    private static String accessUrl(App app, String publicHost) {
        if (app.getStatus() != AppStatus.RUNNING || app.getAssignedPort() == null) {
            return null;
        }
        return "http://" + publicHost + ":" + app.getAssignedPort();
    }
}