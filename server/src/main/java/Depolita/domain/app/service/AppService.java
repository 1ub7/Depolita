package Depolita.domain.app.service;

import Depolita.domain.account.service.AccountValidator;
import Depolita.domain.account.support.GithubRepository;
import Depolita.domain.app.dto.AppCreateRequest;
import Depolita.domain.app.dto.AppLogResponse;
import Depolita.domain.app.dto.AppResponse;
import Depolita.domain.app.entity.App;
import Depolita.domain.app.exception.AppNotFoundException;
import Depolita.domain.app.exception.DuplicateAppNameException;
import Depolita.domain.app.exception.InvalidAppStatusException;
import Depolita.domain.app.repository.AppRepository;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import Depolita.infra.ContainerManager;
import Depolita.infra.ImageBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppService {
    private static final int MAX_CREATE_ATTEMPTS = 3;

    private final AppRepository appRepository;
    private final AccountValidator accountValidator;
    private final PortAllocator portAllocator;
    private final ContainerManager containerManager;
    private final ImageBuilder imageBuilder;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AppResponse create(AppCreateRequest request) {
        GithubRepository repository = accountValidator.validate(request.repositoryUrl());

        for (int attempt = 1; attempt <= MAX_CREATE_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> save(request, repository));
            } catch (DataIntegrityViolationException e) {
                if (appRepository.existsByName(request.name())) {
                    throw new DuplicateAppNameException();
                }
                log.warn("[create] 포트 할당 충돌, 재시도 {}/{}", attempt, MAX_CREATE_ATTEMPTS);
            }
        }
        throw new CustomException(ErrorCode.PORT_ALLOCATION_CONFLICT);
    }

    private AppResponse save(AppCreateRequest request, GithubRepository repository) {
        if (appRepository.existsByName(request.name())) {
            throw new DuplicateAppNameException();
        }
        App app = appRepository.save(App.create(
                request.name(),
                repository.cloneUrl(),
                request.internalPort(),
                portAllocator.allocate()
        ));
        eventPublisher.publishEvent(new AppCreatedEvent(app.getId()));
        return AppResponse.from(app);
    }

    public List<AppResponse> getApps() {
        return appRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AppResponse::from)
                .toList();
    }

    public AppResponse getApp(Long appId) {
        return AppResponse.from(findApp(appId));
    }

    public AppLogResponse getLogs(Long appId, int tail) {
        App app = findApp(appId);
        if (app.getContainerId() == null) {
            String reason = app.getFailureReason();
            return AppLogResponse.of(appId, reason == null ? List.of() : List.of(reason));
        }
        String logs = containerManager.getLogs(app.getContainerId(), tail);
        return AppLogResponse.of(appId, logs.lines().toList());
    }

    @Transactional
    public AppResponse stop(Long appId) {
        App app = findApp(appId);
        app.stop(); // RUNNING이 아니면 InvalidAppStatusException, 포트도 여기서 반납
        containerManager.stop(app.getContainerId()); // 실패하면 예외 → 트랜잭션 롤백으로 상태도 되돌아감
        return AppResponse.from(app);
    }

    @Transactional
    public void delete(Long appId) {
        App app = findApp(appId);
        if (app.isInProgress()) {
            throw new InvalidAppStatusException();
        }
        if (app.getContainerId() != null) {
            containerManager.remove(app.getContainerId());
        }
        imageBuilder.remove(DeployService.imageTag(appId));
        appRepository.delete(app);
    }

    private App findApp(Long appId) {
        return appRepository.findById(appId)
                .orElseThrow(AppNotFoundException::new);
    }
}
