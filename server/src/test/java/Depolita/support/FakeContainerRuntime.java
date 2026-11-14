package Depolita.support;

import Depolita.domain.app.port.ContainerRuntime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FakeContainerRuntime implements ContainerRuntime {

    private record FakeContainer(String id, Long appId, int hostPort, ContainerStatus status) {
        FakeContainer withStatus(ContainerStatus next) {
            return new FakeContainer(id, appId, hostPort, next);
        }
    }

    private final Map<String, FakeContainer> containers = new LinkedHashMap<>();
    private int sequence = 0;

    public String givenRunning(Long appId, int hostPort) {
        return run(appId, "image", "name", hostPort, 8080);
    }

    public ContainerStatus statusOf(String containerId) {
        return inspect(containerId).status();
    }

    public boolean exists(String containerId) {
        return containers.containsKey(containerId);
    }

    @Override
    public String run(Long appId, String image, String name, int hostPort, int containerPort) {
        if (usedHostPorts().contains(hostPort)) {
            throw new IllegalStateException("port is already allocated: " + hostPort);
        }
        String id = "fake-" + (++sequence);
        containers.put(id, new FakeContainer(id, appId, hostPort, ContainerStatus.RUNNING));
        return id;
    }

    @Override
    public void stop(String containerId) {
        containers.computeIfPresent(containerId, (id, c) -> c.withStatus(ContainerStatus.EXITED));
    }

    @Override
    public void remove(String containerId) {
        containers.remove(containerId);
    }

    @Override
    public ContainerInspection inspect(String containerId) {
        FakeContainer container = containerId == null ? null : containers.get(containerId);
        if (container == null) {
            return ContainerInspection.missing();
        }
        return new ContainerInspection(container.status(), false, 0);
    }

    @Override
    public List<ManagedContainer> listManaged() {
        return containers.values().stream()
                .map(c -> new ManagedContainer(c.id(), c.appId()))
                .toList();
    }

    @Override
    public Set<Integer> usedHostPorts() {
        return containers.values().stream()
                .filter(c -> c.status() == ContainerStatus.RUNNING)
                .map(FakeContainer::hostPort)
                .collect(Collectors.toSet());
    }

    @Override
    public String getLogs(String containerId, int tail) {
        return containers.containsKey(containerId) ? "log of " + containerId : "";
    }
}