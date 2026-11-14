package Depolita.domain.app.port;

import java.util.List;
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

    String run(Long appId, String image, String name, int hostPort, int containerPort);

    void stop(String containerId);

    void remove(String containerId);

    ContainerInspection inspect(String containerId);

    List<ManagedContainer> listManaged();

    Set<Integer> usedHostPorts();

    String getLogs(String containerId, int tail);
}