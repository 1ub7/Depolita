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
        AppStatus status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AppResponse from(App app) {
        return new AppResponse(
                app.getId(),
                app.getName(),
                app.getRepositoryUrl(),
                app.getInternalPort(),
                app.getAssignedPort(),
                app.getStatus(),
                app.getFailureReason(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}
