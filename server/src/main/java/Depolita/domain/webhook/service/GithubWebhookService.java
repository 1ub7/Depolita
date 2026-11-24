package Depolita.domain.webhook.service;

import Depolita.domain.account.service.AccountValidator;
import Depolita.domain.account.support.GithubRepository;
import Depolita.domain.app.entity.App;
import Depolita.domain.app.exception.InvalidAppStatusException;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.app.service.AppService;
import Depolita.domain.deployment.entity.DeployTrigger;
import Depolita.domain.webhook.dto.GithubPushPayload;
import Depolita.domain.webhook.dto.WebhookResponse;
import Depolita.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubWebhookService {
    private final AccountValidator accountValidator;
    private final AppRepository appRepository;
    private final AppService appService;

    public WebhookResponse handlePush(GithubPushPayload payload) {
        if (!payload.isDefaultBranchPush()) {
            return WebhookResponse.ignored("기본 브랜치 push 가 아니라서 무시합니다.");
        }

        GithubRepository repository;
        try {
            // Webhook 은 API 키 없이 들어오므로, 허용 계정 검사를 여기서 다시 한다
            repository = accountValidator.validate(payload.repository().cloneUrl());
        } catch (CustomException e) {
            log.warn("[webhook] 허용되지 않은 저장소 {}: {}", payload.repository().cloneUrl(), e.getMessage());
            return WebhookResponse.ignored(e.getMessage());
        }

        // GithubRepository.cloneUrl() 은 앱을 만들 때 저장한 형식과 같다 (owner 소문자, .git)
        List<App> apps = appRepository.findAllByRepositoryUrl(repository.cloneUrl());
        if (apps.isEmpty()) {
            return WebhookResponse.ignored("이 저장소로 배포된 앱이 없습니다.");
        }

        List<Long> redeployed = new ArrayList<>();
        List<Long> skipped = new ArrayList<>();
        for (App app : apps) {
            try {
                appService.redeploy(app.getId(), DeployTrigger.WEBHOOK);
                redeployed.add(app.getId());
            } catch (InvalidAppStatusException e) {
                // 이미 배포 중이면 이번 push 는 건너뛴다 (다음 push 때 최신 커밋이 배포된다)
                log.info("[webhook] appId={} 배포 진행 중이라 건너뜀", app.getId());
                skipped.add(app.getId());
            } catch (CustomException e) {
                // 앱 개수 제한, 포트 고갈 등: 다른 앱 처리는 계속한다
                log.warn("[webhook] appId={} 재배포 실패: {}", app.getId(), e.getMessage());
                skipped.add(app.getId());
            }
        }
        log.info("[webhook] {} commit={} redeployed={} skipped={}",
                repository.cloneUrl(), payload.after(), redeployed, skipped);
        return new WebhookResponse(redeployed, skipped, null);
    }
}
