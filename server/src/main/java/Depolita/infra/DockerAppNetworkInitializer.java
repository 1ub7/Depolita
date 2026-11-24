package Depolita.infra;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 서버가 뜰 때 앱 전용 Docker 네트워크(depolita-apps)가 없으면 만든다.
 * 운영 중에 네트워크가 사라질 수도 있으므로(`docker network prune`, `docker system prune` 은 컨테이너가
 * 붙어 있지 않은 네트워크를 지운다) 컨테이너를 실행하기 직전에도 DockerContainerRuntime 이 ensure() 를 다시 호출한다.
 * enable_icc=false: 같은 네트워크에 있어도 앱 컨테이너끼리는 통신할 수 없다 (외부 인터넷·포트 공개는 그대로 동작).
 * 재조정 루프(AppReconciler)보다 먼저 실행되도록 순서를 앞에 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerAppNetworkInitializer {
    static final Map<String, String> OPTIONS = Map.of("com.docker.network.bridge.enable_icc", "false");

    private final DockerClient dockerClient;

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            ensureAppNetwork();
        } catch (Exception | LinkageError e) {
            log.error("[network] 앱 전용 네트워크 {}를 준비하지 못했습니다. Docker 연결을 확인하세요.",
                    DockerContainerRuntime.APP_NETWORK, e);
        }
    }

    void ensureAppNetwork() {
        ensure(dockerClient);
    }

    /** 없으면 만든다. 이미 있으면 아무것도 하지 않는다 (여러 번 호출해도 안전) */
    static void ensure(DockerClient dockerClient) {
        boolean exists = dockerClient.listNetworksCmd()
                .withNameFilter(DockerContainerRuntime.APP_NETWORK)
                .exec()
                .stream()
                .anyMatch(network -> DockerContainerRuntime.APP_NETWORK.equals(network.getName()));
        if (exists) {
            return;
        }
        dockerClient.createNetworkCmd()
                .withName(DockerContainerRuntime.APP_NETWORK)
                .withDriver("bridge")
                .withOptions(OPTIONS)
                .exec();
        log.info("[network] 앱 전용 네트워크 {} 생성 (icc=false)", DockerContainerRuntime.APP_NETWORK);
    }
}
