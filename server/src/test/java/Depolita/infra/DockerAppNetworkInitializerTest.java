package Depolita.infra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.command.ListNetworksCmd;
import com.github.dockerjava.api.model.Network;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DockerAppNetworkInitializerTest {
    private final DockerClient dockerClient = mock(DockerClient.class);
    private final ListNetworksCmd listCmd = mock(ListNetworksCmd.class, RETURNS_SELF);
    private final CreateNetworkCmd createCmd = mock(CreateNetworkCmd.class, RETURNS_SELF);
    private final DockerAppNetworkInitializer initializer = new DockerAppNetworkInitializer(dockerClient);

    private static Network network(String name) {
        Network network = mock(Network.class);
        when(network.getName()).thenReturn(name);
        return network;
    }

    private void givenNetworks(Network... networks) {
        when(dockerClient.listNetworksCmd()).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(List.of(networks));
        when(dockerClient.createNetworkCmd()).thenReturn(createCmd);
    }

    @Test
    @DisplayName("네트워크가 없으면 icc=false 옵션으로 만든다")
    void createsWhenMissing() {
        givenNetworks();

        initializer.ensureAppNetwork();

        verify(createCmd).withName(DockerContainerRuntime.APP_NETWORK);
        verify(createCmd).withOptions(DockerAppNetworkInitializer.OPTIONS);
        verify(createCmd).exec();
    }

    @Test
    @DisplayName("이미 있으면 다시 만들지 않는다")
    void skipsWhenExists() {
        givenNetworks(network(DockerContainerRuntime.APP_NETWORK));

        initializer.ensureAppNetwork();

        verify(dockerClient, never()).createNetworkCmd();
    }

    @Test
    @DisplayName("이름이 부분 일치하는 다른 네트워크만 있으면 새로 만든다")
    void partialNameMatchIsNotEnough() {
        givenNetworks(network(DockerContainerRuntime.APP_NETWORK + "-old"));

        initializer.ensureAppNetwork();

        verify(createCmd).exec();
    }

    @Test
    @DisplayName("Docker에 연결할 수 없어도 서버 기동을 막지 않는다")
    void startupDoesNotThrow() {
        when(dockerClient.listNetworksCmd()).thenThrow(new RuntimeException("Cannot connect to the Docker daemon"));

        assertDoesNotThrow(initializer::onStartup);
    }
}
