package Depolita.domain.app.service;

import Depolita.domain.app.entity.App;
import Depolita.domain.app.exception.AppNotFoundException;
import Depolita.domain.app.exception.DockerfileNotFoundException;
import Depolita.domain.app.repository.AppRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.nio.file.Files;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeployService {
    private static final int MAX_REASON_LENGTH = 500;

    private final AppRepository appRepository;
    private final GitCloner gitCloner;
    private final ImageBuilder imageBuilder;
    private final ContainerManager containerManager;
    private final TransactionTemplate transactionTemplate;

    @Async("deployExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deploy(AppCreatedEvent event) {
        Long appId = event.appId();
        Path workDir = null;
        try {
            App app = appRepository.findById(appId).orElseThrow(AppNotFoundException::new);
            workDir = gitCloner.clone(app.getRepositoryUrl();
            if (!Files.exists(workDir.resolve("Dockerfile"))) {
                throw new DockerfileNotFoundException();
            }

            updateApp(appId, a -> a.)
        }
    }
}
