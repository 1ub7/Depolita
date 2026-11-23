package Depolita.infra;

import Depolita.domain.app.port.ContainerRuntime;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class DockerContainerRuntime implements ContainerRuntime {
    public static final String APP_ID_LABEL = "depolita.app-id";

    private static final long MEMORY_LIMIT = 512L * 1024 * 1024;
    private static final long NANO_CPUS = 500_000_000L;
    private static final int MAX_LOG_TAIL = 1000;
    static final Map<String, String> LOG_OPTIONS = Map.of("max-size", "10m", "max-file", "3");

    private final DockerClient dockerClient;

    @Override
    public String run(Long appId, String image, String name, int hostPort, int containerPort) {
        ExposedPort exposed = ExposedPort.tcp(containerPort);
        HostConfig hostConfig = hostConfig(hostPort, exposed);

        String containerId = null;
        try {
            containerId = dockerClient.createContainerCmd(image)
                    .withName(name)
                    .withLabels(Map.of(APP_ID_LABEL, String.valueOf(appId)))
                    .withExposedPorts(exposed)
                    .withHostConfig(hostConfig)
                    .exec()
                    .getId();
            dockerClient.startContainerCmd(containerId).exec();
            return containerId;
        } catch (Exception e) {
            if (containerId != null) remove(containerId);
            throw new CustomException(ErrorCode.CONTAINER_RUN_FAILED, e);
        }
    }

    static HostConfig hostConfig(int hostPort, ExposedPort exposed) {
        return HostConfig.newHostConfig()
                .withPortBindings(new PortBinding(Ports.Binding.bindPort(hostPort), exposed))
                .withMemory(MEMORY_LIMIT)
                .withMemorySwap(MEMORY_LIMIT)
                .withNanoCPUs(NANO_CPUS)
                .withRestartPolicy(RestartPolicy.unlessStoppedRestart())
                .withLogConfig(new LogConfig(LogConfig.LoggingType.JSON_FILE, LOG_OPTIONS));
    }

    @Override
    public void stop(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
        } catch (NotModifiedException | NotFoundException ignored) {
        }
    }

    @Override
    public void remove(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (NotFoundException ignored) {
        }
    }

    @Override
    public ContainerInspection inspect(String containerId) {
        if (containerId == null) {
            return ContainerInspection.missing();
        }
        try {
            InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = response.getState();
            boolean oomKilled = Boolean.TRUE.equals(state.getOOMKilled());
            int restartCount = Objects.requireNonNullElse(response.getRestartCount(), 0);

            ContainerStatus status;
            if (Boolean.TRUE.equals(state.getRestarting())) {
                status = ContainerStatus.RESTARTING;
            } else if (Boolean.TRUE.equals(state.getRunning())) {
                status = ContainerStatus.RUNNING;
            } else {
                status = ContainerStatus.EXITED;
            }
            return new ContainerInspection(status, oomKilled, restartCount);
        } catch (NotFoundException e) {
            return ContainerInspection.missing();
        }
    }

    @Override
    public List<ManagedContainer> listManaged() {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(List.of(APP_ID_LABEL))
                .exec()
                .stream()
                .map(c -> new ManagedContainer(c.getId(), parseAppId(c.getLabels())))
                .filter(c -> c.appId() != null)
                .toList();
    }

    @Override
    public Set<Integer> usedHostPorts() {
        return dockerClient.listContainersCmd()
                .exec()
                .stream()
                .flatMap(c -> c.getPorts() == null ? Stream.<ContainerPort>empty() : Arrays.stream(c.getPorts()))
                .map(ContainerPort::getPublicPort)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public String getLogs(String containerId, int tail) {
        StringBuffer logs = new StringBuffer();
        try {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(Math.min(tail, MAX_LOG_TAIL))
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            logs.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                        }
                    })
                    .awaitCompletion(5, TimeUnit.SECONDS);
        } catch (NotFoundException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return logs.toString();
    }

    private static Long parseAppId(Map<String, String> labels) {
        if (labels == null) return null;
        try {
            return Long.valueOf(labels.get(APP_ID_LABEL));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
