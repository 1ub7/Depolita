package Depolita.domain.app.service;

/**
 * 배포 한 번이 끝났다 (성공 또는 실패).
 *
 * @param appName   앱을 읽기 전에 실패하면 null
 * @param commitSha 복제 전에 실패하면 null
 * @param reason    실패 원인 (성공이면 null)
 */
public record DeploymentFinishedEvent(
        Long appId,
        String appName,
        boolean success,
        String commitSha,
        Integer port,
        String reason
) {
    public static DeploymentFinishedEvent succeeded(Long appId, String appName, String commitSha, Integer port) {
        return new DeploymentFinishedEvent(appId, appName, true, commitSha, port, null);
    }

    public static DeploymentFinishedEvent failed(Long appId, String appName, String commitSha, String reason) {
        return new DeploymentFinishedEvent(appId, appName, false, commitSha, null, reason);
    }
}
