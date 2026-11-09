package Depolita.domain.app.service;

import Depolita.domain.account.service.AccountValidator;
import Depolita.domain.app.dto.AppCreateRequest;
import Depolita.domain.app.dto.AppLogResponse;
import Depolita.domain.app.dto.AppResponse;
import Depolita.domain.app.entity.App;
import Depolita.domain.app.entity.AppStatus;
import Depolita.domain.app.repository.AppRepository;
import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;
import Depolita.infra.ContainerManager;
import Depolita.infra.ImageBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppService {
    private final AppRepository appRepository;
    private final AccountValidator accountValidator;
    private final PortAllocator portAllocator;
    private final ContainerManager containerManager;
    private final ImageBuilder imageBuilder;
    private final ApplicationEventPublisher eventPublisher;


    @Transactional
    public AppResponse create(AppCreateRequest request) {
        accountValidator.validate(request.repositoryUrl());
        if (appRepository.existsByName(request.name())) {
            throw new CustomException(ErrorCode.DUPLICATE_APP_NAME);
        }

        App app = appRepository.save(App.create(
                request.name(),
                request.repositoryUrl(),
                request.internalPort(),
                portAllocator.allocate()
        ));

        eventPublisher.publishEvent(new AppCreatedEvent(app.getId()));
        return AppResponse.from(app);
    }

    public List<AppResponse> getApps() {
        return appRepository.findAll().stream()
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
        if (app.getStatus() != AppStatus.RUNNING) {
            throw new CustomException(ErrorCode.INVALID_APP_STATUS);
        }
        containerManager.stop(app.getContainerId());
        app.changeStatus(AppStatus.STOPPED);
        return AppResponse.from(app);
    }

    @Transactional
    public void delete(Long appId) {
        App app = findApp(appId);

        if (app.getStatus() == AppStatus.CLONING || app.getStatus() == AppStatus.BUILDING) {
            throw new CustomException(ErrorCode.INVALID_APP_STATUS);
        }
        if (app.getContainerId() != null) {
            containerManager.remove(app.getContainerId());
        }
        imageBuilder.remove(DeployService.imageTag(appId));
        appRepository.delete(app);
    }

    private App findApp(Long appId) {
        return appRepository.findById(appId)
                .orElseThrow(() -> new CustomException(ErrorCode.APP_NOT_FOUND));
    }
}
