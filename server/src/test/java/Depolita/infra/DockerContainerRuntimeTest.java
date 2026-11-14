package Depolita.infra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
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
}
