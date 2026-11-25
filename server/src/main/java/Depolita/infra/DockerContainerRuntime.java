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
import com.github.dockerjava.api.model.CpuStatsConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StatsConfig;
import com.github.dockerjava.core.InvocationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    /** 앱 전용 네트워크. 앱 컨테이너끼리 통신할 수 없도록 icc=false 로 만든다 (DockerAppNetworkInitializer) */
    public static final String APP_NETWORK = "depolita-apps";
    /** setuid 바이너리 등으로 권한을 올리는 것을 막는다 */
    static final String NO_NEW_PRIVILEGES = "no-new-privileges";
    /** fork bomb 처럼 프로세스를 무한히 만들어 호스트를 멈추는 것을 막는다 */
    static final long PIDS_LIMIT = 256L;
    private static final long STATS_TIMEOUT_SECONDS = 5;

    private final DockerClient dockerClient;

    @Override
    public String run(Long appId, String image, String name, int hostPort, int containerPort) {
        ExposedPort exposed = ExposedPort.tcp(containerPort);
        HostConfig hostConfig = hostConfig(hostPort, exposed);

        String containerId = null;
        try {
            // 기동 후 네트워크가 지워졌으면(prune 등) 모든 배포가 실패하므로, 실행 직전에 한 번 더 확인한다
            DockerAppNetworkInitializer.ensure(dockerClient);
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
                .withLogConfig(new LogConfig(LogConfig.LoggingType.JSON_FILE, LOG_OPTIONS))
                .withSecurityOpts(List.of(NO_NEW_PRIVILEGES))
                .withPidsLimit(PIDS_LIMIT)
                .withNetworkMode(APP_NETWORK);
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

    @Override
    public Optional<ContainerStats> stats(String containerId) {
        if (containerId == null) {
            return Optional.empty();
        }
        try (InvocationBuilder.AsyncResultCallback<Statistics> callback = new InvocationBuilder.AsyncResultCallback<>()) {
            // noStream: 한 번만 받는다. Docker 가 이전 측정값(precpu)을 같이 채워줘서 CPU 사용률을 계산할 수 있다
            dockerClient.statsCmd(containerId).withNoStream(true).exec(callback);
            // Docker 가 응답하지 않으면 요청 스레드가 영원히 묶이므로 기다리는 시간을 제한한다
            // (precpu 를 채우려고 Docker 가 두 번 측정하므로 보통 1~2초 걸린다)
            if (!callback.awaitCompletion(STATS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return Optional.empty();
            }
            Statistics stats = callback.awaitResult();
            if (stats == null || stats.getCpuStats() == null || stats.getMemoryStats() == null) {
                return Optional.empty();
            }
            return Optional.of(new ContainerStats(
                    cpuPercent(stats),
                    memoryUsage(stats.getMemoryStats()),
                    orZero(stats.getMemoryStats().getLimit())));
        } catch (NotFoundException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double cpuPercent(Statistics stats) {
        CpuStatsConfig now = stats.getCpuStats();
        CpuStatsConfig before = stats.getPreCpuStats();
        if (before == null || now.getCpuUsage() == null || before.getCpuUsage() == null) {
            return 0.0;
        }
        return cpuPercent(
                orZero(now.getCpuUsage().getTotalUsage()) - orZero(before.getCpuUsage().getTotalUsage()),
                orZero(now.getSystemCpuUsage()) - orZero(before.getSystemCpuUsage()),
                orZero(now.getOnlineCpus()));
    }

    private static long memoryUsage(MemoryStatsConfig memory) {
        StatsConfig detail = memory.getStats();
        return memoryUsage(
                orZero(memory.getUsage()),
                detail == null ? null : detail.getTotalInactiveFile(),
                detail == null ? null : detail.getInactiveFile());
    }

    /**
     * docker stats 와 같은 계산식: 사용량에서 회수 가능한 파일 캐시(inactive_file)를 뺀다.
     * usage 에는 페이지 캐시가 포함돼서, 파일을 읽기만 하는 앱도 512MB 에 가깝게 보이기 때문이다.
     * cgroup v1 은 total_inactive_file, v2(Ubuntu 24.04 기본)는 inactive_file 을 쓴다.
     */
    static long memoryUsage(long usage, Long totalInactiveFile, Long inactiveFile) {
        Long inactive = totalInactiveFile != null ? totalInactiveFile : inactiveFile;
        if (inactive != null && inactive < usage) {
            return usage - inactive;
        }
        return usage;
    }

    /** docker stats 와 같은 계산식: (컨테이너 CPU 증가량 / 호스트 전체 CPU 증가량) × 코어 수 × 100 */
    static double cpuPercent(long containerDelta, long systemDelta, long onlineCpus) {
        if (containerDelta <= 0 || systemDelta <= 0 || onlineCpus <= 0) {
            return 0.0;
        }
        return (double) containerDelta / systemDelta * onlineCpus * 100.0;
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
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
