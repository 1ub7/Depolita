package Depolita.infra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.command.ListNetworksCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.LogConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DockerContainerRuntimeTest {
    private final DockerClient dockerClient = mock(DockerClient.class);
    private final DockerContainerRuntime runtime = new DockerContainerRuntime(dockerClient);

    private void givenStopThrows(RuntimeException e) {
        StopContainerCmd cmd = mock(StopContainerCmd.class, RETURNS_SELF);
        when(cmd.exec()).thenThrow(e);
        when(dockerClient.stopContainerCmd("c-1")).thenReturn(cmd);
    }

    @Test
    @DisplayName("외부에서 삭제된 컨테이너를 중지해도 예외가 나지 않는다")
    void stopMissingContainer() {
        givenStopThrows(new NotFoundException("No such container: c-1"));

        assertDoesNotThrow(() -> runtime.stop("c-1"));
    }

    @Test
    @DisplayName("이미 중지된 컨테이너를 중지해도 예외가 나지 않는다")
    void stopAlreadyStoppedContainer() {
        givenStopThrows(new NotModifiedException("Container already stopped"));

        assertDoesNotThrow(() -> runtime.stop("c-1"));
    }

    @Test
    @DisplayName("외부에서 삭제된 컨테이너의 로그는 빈 문자열")
    void logsOfMissingContainer() {
        LogContainerCmd cmd = mock(LogContainerCmd.class, RETURNS_SELF);
        when(cmd.exec(any())).thenThrow(new NotFoundException("No such container: c-1"));
        when(dockerClient.logContainerCmd("c-1")).thenReturn(cmd);

        assertEquals("", runtime.getLogs("c-1", 100));
    }

    @Test
    @DisplayName("앱 컨테이너는 json-file 로그를 10MB x 3개로 제한한다")
    void logRotation() {
        HostConfig config = DockerContainerRuntime.hostConfig(10001, ExposedPort.tcp(8080));

        assertEquals(LogConfig.LoggingType.JSON_FILE, config.getLogConfig().getType());
        assertEquals("10m", config.getLogConfig().getConfig().get("max-size"));
        assertEquals("3", config.getLogConfig().getConfig().get("max-file"));
    }

    @Test
    @DisplayName("앱 컨테이너는 권한 상승을 막고 프로세스 수를 제한한다")
    void securityOptions() {
        HostConfig config = DockerContainerRuntime.hostConfig(10001, ExposedPort.tcp(8080));

        assertTrue(config.getSecurityOpts().contains("no-new-privileges"));
        assertEquals(256L, config.getPidsLimit());
    }

    @Test
    @DisplayName("앱 컨테이너는 앱 간 통신이 막힌 전용 네트워크에 붙는다")
    void appNetwork() {
        HostConfig config = DockerContainerRuntime.hostConfig(10001, ExposedPort.tcp(8080));

        assertEquals(DockerContainerRuntime.APP_NETWORK, config.getNetworkMode());
    }

    @Test
    @DisplayName("실행 직전에 앱 전용 네트워크가 없으면(prune 등으로 지워진 경우) 다시 만든 뒤 컨테이너를 만든다")
    void runRecreatesMissingNetwork() {
        ListNetworksCmd listCmd = mock(ListNetworksCmd.class, RETURNS_SELF);
        when(listCmd.exec()).thenReturn(List.of());
        when(dockerClient.listNetworksCmd()).thenReturn(listCmd);
        CreateNetworkCmd createNetworkCmd = mock(CreateNetworkCmd.class, RETURNS_SELF);
        when(dockerClient.createNetworkCmd()).thenReturn(createNetworkCmd);

        CreateContainerCmd createCmd = mock(CreateContainerCmd.class, RETURNS_SELF);
        CreateContainerResponse created = mock(CreateContainerResponse.class);
        when(created.getId()).thenReturn("c-1");
        when(createCmd.exec()).thenReturn(created);
        when(dockerClient.createContainerCmd("image")).thenReturn(createCmd);
        when(dockerClient.startContainerCmd("c-1")).thenReturn(mock(StartContainerCmd.class));

        assertEquals("c-1", runtime.run(1L, "image", "depolita-app-1", 10001, 8080));

        InOrder order = inOrder(createNetworkCmd, createCmd);
        order.verify(createNetworkCmd).exec();
        order.verify(createCmd).exec();
    }

    @Test
    @DisplayName("CPU 사용률: 컨테이너 증가량 / 호스트 증가량 × 코어 수 × 100")
    void cpuPercent() {
        // 호스트 전체가 1초(1e9ns) 동안, 컨테이너가 0.25초를 썼고 코어가 2개 → 50%
        assertEquals(50.0, DockerContainerRuntime.cpuPercent(250_000_000L, 1_000_000_000L, 2), 0.0001);
    }

    @Test
    @DisplayName("이전 측정값이 없어 증가량이 0이거나 음수면 0%")
    void cpuPercentWithoutDelta() {
        assertEquals(0.0, DockerContainerRuntime.cpuPercent(0, 1_000_000_000L, 2));
        assertEquals(0.0, DockerContainerRuntime.cpuPercent(100, 0, 2));
        assertEquals(0.0, DockerContainerRuntime.cpuPercent(-5, 100, 2));
    }

    @Test
    @DisplayName("컨테이너 ID가 없으면 조회하지 않고 empty")
    void statsWithoutContainer() {
        assertTrue(runtime.stats(null).isEmpty());
    }

    @Test
    @DisplayName("메모리 사용량은 docker stats 처럼 회수 가능한 파일 캐시(inactive_file)를 뺀다")
    void memoryUsageExcludesInactiveFileCache() {
        long mb = 1024L * 1024;
        // cgroup v2: inactive_file
        assertEquals(100 * mb, DockerContainerRuntime.memoryUsage(400 * mb, null, 300 * mb));
        // cgroup v1: total_inactive_file 우선
        assertEquals(150 * mb, DockerContainerRuntime.memoryUsage(400 * mb, 250 * mb, 300 * mb));
        // 정보가 없거나 이상한 값이면 usage 그대로
        assertEquals(400 * mb, DockerContainerRuntime.memoryUsage(400 * mb, null, null));
        assertEquals(400 * mb, DockerContainerRuntime.memoryUsage(400 * mb, null, 500 * mb));
    }
}
