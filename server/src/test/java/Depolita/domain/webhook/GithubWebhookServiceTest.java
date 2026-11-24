package Depolita.domain.webhook;

import Depolita.domain.account.config.AllowedAccountProperties;
import Depolita.domain.account.service.AccountValidator;
import Depolita.domain.app.entity.App;
import Depolita.domain.app.exception.AppLimitExceededException;
import Depolita.domain.app.exception.InvalidAppStatusException;
import Depolita.domain.app.repository.AppRepository;
import Depolita.domain.app.service.AppService;
import Depolita.domain.deployment.entity.DeployTrigger;
import Depolita.domain.webhook.dto.GithubPushPayload;
import Depolita.domain.webhook.dto.GithubPushPayload.Repository;
import Depolita.domain.webhook.dto.WebhookResponse;
import Depolita.domain.webhook.service.GithubWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GithubWebhookServiceTest {
    private static final String CLONE_URL = "https://github.com/1ub7/demo.git";

    private final AppRepository appRepository = mock(AppRepository.class);
    private final AppService appService = mock(AppService.class);
    private final GithubWebhookService service = new GithubWebhookService(
            new AccountValidator(new AllowedAccountProperties(Set.of("1ub7"))), appRepository, appService);

    private static GithubPushPayload push(String ref, String cloneUrl) {
        return new GithubPushPayload(ref, "abc123", false, new Repository(cloneUrl, "main"));
    }

    private static App app(Long id) {
        App app = App.create("app-" + id, CLONE_URL, 8080, 10000 + id.intValue());
        ReflectionTestUtils.setField(app, "id", id);
        return app;
    }

    @Test
    @DisplayName("기본 브랜치 push → 같은 저장소의 앱을 모두 WEBHOOK 트리거로 재배포")
    void redeploysMatchingApps() {
        when(appRepository.findAllByRepositoryUrl(CLONE_URL)).thenReturn(List.of(app(1L), app(2L)));

        WebhookResponse response = service.handlePush(push("refs/heads/main", CLONE_URL));

        assertEquals(List.of(1L, 2L), response.redeployed());
        verify(appService).redeploy(1L, DeployTrigger.WEBHOOK);
        verify(appService).redeploy(2L, DeployTrigger.WEBHOOK);
    }

    @Test
    @DisplayName("기본 브랜치가 아니면 아무것도 하지 않는다")
    void ignoresOtherBranches() {
        service.handlePush(push("refs/heads/feature", CLONE_URL));

        verifyNoInteractions(appRepository, appService);
    }

    @Test
    @DisplayName("허용되지 않은 계정의 저장소는 무시한다 (Webhook 은 API 키 없이 들어오므로)")
    void ignoresNotAllowedAccount() {
        WebhookResponse response = service.handlePush(push("refs/heads/main", "https://github.com/stranger/demo.git"));

        assertEquals(List.of(), response.redeployed());
        verifyNoInteractions(appRepository, appService);
    }

    @Test
    @DisplayName("배포 중인 앱은 건너뛰고 나머지 앱은 계속 처리한다")
    void skipsInProgressApps() {
        when(appRepository.findAllByRepositoryUrl(CLONE_URL)).thenReturn(List.of(app(1L), app(2L)));
        doThrow(new InvalidAppStatusException()).when(appService).redeploy(1L, DeployTrigger.WEBHOOK);

        WebhookResponse response = service.handlePush(push("refs/heads/main", CLONE_URL));

        assertEquals(List.of(2L), response.redeployed());
        assertEquals(List.of(1L), response.skipped());
    }

    @Test
    @DisplayName("앱 개수 제한 같은 다른 오류도 건너뛰고 계속 처리한다")
    void skipsOnOtherErrors() {
        when(appRepository.findAllByRepositoryUrl(CLONE_URL)).thenReturn(List.of(app(1L)));
        doThrow(new AppLimitExceededException()).when(appService).redeploy(1L, DeployTrigger.WEBHOOK);

        WebhookResponse response = service.handlePush(push("refs/heads/main", CLONE_URL));

        assertEquals(List.of(1L), response.skipped());
    }

    @Test
    @DisplayName("이 저장소로 배포된 앱이 없으면 재배포하지 않는다")
    void noMatchingApps() {
        when(appRepository.findAllByRepositoryUrl(any())).thenReturn(List.of());

        service.handlePush(push("refs/heads/main", CLONE_URL));

        verify(appService, never()).redeploy(anyLong(), any());
    }

    @Test
    @DisplayName("중지된 앱은 push 가 와도 되살리지 않는다")
    void skipsStoppedApps() {
        App stopped = app(1L);
        stopped.startBuilding();
        stopped.run("c-1");
        stopped.stop();
        when(appRepository.findAllByRepositoryUrl(CLONE_URL)).thenReturn(List.of(stopped, app(2L)));

        WebhookResponse response = service.handlePush(push("refs/heads/main", CLONE_URL));

        assertEquals(List.of(2L), response.redeployed());
        assertEquals(List.of(1L), response.skipped());
        verify(appService, never()).redeploy(1L, DeployTrigger.WEBHOOK);
    }
}
