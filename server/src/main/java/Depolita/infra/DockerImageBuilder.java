package Depolita.infra;

import Depolita.domain.app.port.ImageBuilder;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.BuildResponseItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class DockerImageBuilder implements ImageBuilder {
    private static final int LOG_TAIL_LENGTH = 300;
    static final long BUILD_MEMORY = 1024L * 1024 * 1024;        
    static final long BUILD_MEMORY_SWAP = 2048L * 1024 * 1024;   
    static final String BUILD_CPUSET = "0";
    private final DockerClient dockerClient;

    @Override
    public String build(Path contextDir, String tag) {
        StringBuffer logs = new StringBuffer();
        try {
            return dockerClient.buildImageCmd(contextDir.toFile())
                    .withTags(Set.of(tag))
                    .withMemory(BUILD_MEMORY)
                    .withMemswap(BUILD_MEMORY_SWAP)
                    .withCpusetcpus(BUILD_CPUSET)
                    .exec(new BuildImageResultCallback() {
                        @Override
                        public void onNext(BuildResponseItem item) {
                            if (item.getStream() != null) logs.append(item.getStream());
                            super.onNext(item);
                        }
                    })
                    .awaitImageId(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.IMAGE_BUILD_FAILED, tail(logs), e);
        }
    }

    @Override
    public void remove(String tag) {
        try {
            dockerClient.removeImageCmd(tag).withForce(true).exec();
        } catch (NotFoundException ignored) {}
    }

    private String tail(StringBuffer logs) {
        int start = Math.max(0, logs.length() - LOG_TAIL_LENGTH);
        return logs.substring(start);
    }
}
