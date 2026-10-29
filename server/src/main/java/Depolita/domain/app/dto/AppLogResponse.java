package Depolita.domain.app.dto;

import java.util.List;

public record AppLogResponse(
        Long appId,
        List<String> lines
) {
    public static AppLogResponse of(Long appId, List<String> lines) {
        return new AppLogResponse(appId, lines);
    }
}
