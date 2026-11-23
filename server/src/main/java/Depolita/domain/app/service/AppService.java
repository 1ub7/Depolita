package Depolita.domain.app.service;

import Depolita.domain.account.service.AccountValidator;
import Depolita.domain.account.support.GithubRepository;
import Depolita.domain.app.dto.AppCreateRequest;
import Depolita.domain.app.dto.AppLogResponse;
import Depolita.domain.app.dto.AppResponse;
import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.exception.AppLimitExceededException;
import Depolita.domain.app.exception.AppNotFoundException;
import Depolita.domain.app.exception.DuplicateAppNameException;
import Depolita.domain.app.exception.InvalidAppStatusException;
import Depolita.domain.app.port.ContainerRuntime;
import Depolita.domain.app.port.ImageBuilder;
import Depolita.domain.app.repository.AppRepository;
import Depolita.global.config.DepolitaProperties;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppService {
    private static final int MAX_CREATE_ATTEMPTS = 3;
    static final int MAX_ACTIVE_APPS = 5;
    private static final Set<AppStatus> ACTIVE_STATUSES =
            EnumSet.of(AppStatus.CLONING, AppStatus.BUILDING, AppStatus.RUNNING);

    private final AppRepository appRepository;
    private final AccountValidator accountValidator;
    private final PortAllocator portAllocator;
    private final ContainerRuntime containerRuntime;
    private final ImageBuilder imageBuilder;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final DepolitaProperties properties;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AppResponse create(AppCreateRequest request) {
        GithubRepository repository = accountValidator.validate(request.repositoryUrl());
        Set<Integer> inUseOnHost = usedHostPorts();

        for (int attempt = 1; attempt <= MAX_CREATE_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> save(request, repository, inUseOnHost));
            } catch (DataIntegrityViolationException e) {
                if (appRepository.existsByName(request.name())) {
                    throw new DuplicateAppNameException();
                }
                log.warn("[create] 포트 할당 충돌, 재시도 {}/{}", attempt, MAX_CREATE_ATTEMPTS);
            }
        }
        throw new CustomException(ErrorCode.PORT_ALLOCATION_CONFLICT);
    }

    private AppResponse save(AppCreateRequest request, GithubRepository repository, Set<Integer> inUseOnHost) {
        if (appRepository.existsByName(request.name())) {
            throw new DuplicateAppNameException();
        }
        if (appRepository.countByStatusIn(ACTIVE_STATUSES) >= MAX_ACTIVE_APPS) {
            throw new AppLimitExceededException();
        }
        App app = appRepository.save(App.create(
                request.name(),
                repository.cloneUrl(),
                request.internalPort(),
                portAllocator.allocate(inUseOnHost)
        ));
        eventPublisher.publishEvent(new AppCreatedEvent(app.getId()));
        return toResponse(app);
    }

    private Set<Integer> usedHostPorts() {
        try {
            return containerRuntime.usedHostPorts();
        } catch (Exception e) {
            log.warn("[create] Docker 포트 조회 실패, DB 기준으로만 포트를 고릅니다", e);
            return Set.of();
        }
    }

    public List<AppResponse> getApps() {
        return appRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public AppResponse getApp(Long appId) {
        return toResponse(findApp(appId));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AppLogResponse getLogs(Long appId, int tail) {
        App app = findApp(appId);
        if (app.getContainerId() == null) {
            String reason = app.getFailureReason();
            return AppLogResponse.of(appId, reason == null ? List.of() : List.of(reason));
        }
        String logs = containerRuntime.getLogs(app.getContainerId(), tail);
        return AppLogResponse.of(appId, logs.lines().toList());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AppResponse stop(Long appId) {
        App app = transactionTemplate.execute(status -> {
            App found = findApp(appId);
            found.stop();
            return found;
        });

        try {
            containerRuntime.stop(app.getContainerId());
        } catch (Exception e) {
            log.warn("[stop] appId={} 컨테이너 중지 실패, 재조정 루프가 다시 시도합니다", appId, e);
        }
        return toResponse(app);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void delete(Long appId) {
        String containerId = transactionTemplate.execute(status -> {
            App app = findApp(appId);
            if (app.isInProgress()) {
                throw new InvalidAppStatusException();
            }
            appRepository.delete(app);
            return app.getContainerId();
        });
        try {
            if (containerId != null) {
                containerRuntime.remove(containerId);
            }
            imageBuilder.remove(DeployService.imageTag(appId));
        } catch (Exception e) {
            log.warn("[delete] appId={} Docker 리소스 정리 실패, 재조정 루프가 정리합니다", appId, e);
        }
    }

    private App findApp(Long appId) {
        return appRepository.findById(appId)
                .orElseThrow(AppNotFoundException::new);
    }

    private AppResponse toResponse(App app) {
        return AppResponse.from(app, properties.publicHost());
    }
}
