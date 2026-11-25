package Depolita.domain.app.dto;

import Depolita.domain.app.port.ContainerRuntime.ContainerStats;

/**
 * @param cpuPercent CPU 1개 = 100% 기준. 앱 컨테이너는 0.5 CPU 로 제한되어 있어 최대 약 50%
 */
public record AppStatsResponse(
        Long appId,
        double cpuPercent,
        long memoryUsageBytes,
        long memoryLimitBytes
) {
    public static AppStatsResponse of(Long appId, ContainerStats stats) {
        // 소수점 한 자리로 반올림해서 화면에서 따로 다듬지 않게 한다
        double cpu = Math.round(stats.cpuPercent() * 10) / 10.0;
        return new AppStatsResponse(appId, cpu, stats.memoryUsageBytes(), stats.memoryLimitBytes());
    }
}
