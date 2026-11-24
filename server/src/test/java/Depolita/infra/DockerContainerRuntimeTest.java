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
}
