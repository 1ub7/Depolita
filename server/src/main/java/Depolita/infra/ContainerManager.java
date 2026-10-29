package Depolita.infra;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import com.github.dockerjava.api.model.Frame;

@Component
@RequiredArgsConstructor
public class ContainerManager {
    private static final long MEMORY_LIMIT = 512L * 1024 * 1024;
    private static final long NANO_CPUS = 500_000_000L;
    private static final int MAX_LOG_TAIL = 1000;

    private final DockerClient dockerClient;

    public String run(String image, String name, int hostPort, int containerPort) {
        ExposedPort exposed = ExposedPort.tcp(containerPort);
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPortBindings(new PortBinding(Ports.Binding.bindPort(hostPort), exposed))
                .withMemory(MEMORY_LIMIT)
                .withMemorySwap(MEMORY_LIMIT)
                .withNanoCPUs(NANO_CPUS)
                .withRestartPolicy(RestartPolicy.unlessStoppedRestart());

        String containerId = null;
        try {
            containerId = dockerClient.createContainerCmd(image)
                    .withName(name)
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

    public void stop(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
        } catch (NotModifiedException ignored) {
        }
    }

    public void remove(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (NotFoundException ignored) {
        }
    }

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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return logs.toString();
    }
}
