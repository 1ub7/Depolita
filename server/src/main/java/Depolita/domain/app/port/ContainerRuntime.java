package Depolita.domain.app.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContainerRuntime {

    enum ContainerStatus { RUNNING, RESTARTING, EXITED, MISSING }

    record ContainerInspection(ContainerStatus status, boolean oomKilled, int restartCount) {
        public static ContainerInspection missing() {
            return new ContainerInspection(ContainerStatus.MISSING, false, 0);
        }
    }

    record ManagedContainer(String id, Long appId) {
    }

    /**
     * 컨테이너 리소스 사용량 한 순간의 값.
     *
     * @param cpuPercent 호스트 CPU 1개 = 100% 기준 (docker stats 와 같은 계산)
     */
    record ContainerStats(double cpuPercent, long memoryUsageBytes, long memoryLimitBytes) {
    }

    String run(Long appId, String image, String name, int hostPort, int containerPort);

    void stop(String containerId);

    void remove(String containerId);

    ContainerInspection inspect(String containerId);

    List<ManagedContainer> listManaged();

    Set<Integer> usedHostPorts();

    String getLogs(String containerId, int tail);

    /** 실행 중이 아니거나 컨테이너가 없으면 empty */
    Optional<ContainerStats> stats(String containerId);
}
