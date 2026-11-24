package Depolita.domain.webhook;

import Depolita.domain.webhook.config.WebhookProperties;
import Depolita.domain.webhook.controller.GithubWebhookController;
import Depolita.domain.webhook.dto.GithubPushPayload;
import Depolita.domain.webhook.dto.WebhookResponse;
import Depolita.domain.webhook.service.GithubWebhookService;
import Depolita.domain.webhook.support.WebhookSignatureVerifier;
import Depolita.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GithubWebhookControllerTest {
    private static final String SECRET = "webhook-secret";
    private static final String PUSH_BODY = """
            {"ref":"refs/heads/main","after":"abc123","deleted":false,
             "repository":{"clone_url":"https://github.com/1ub7/demo.git","default_branch":"main","name":"demo"},
             "pusher":{"name":"1ub7"}}""";

    private final GithubWebhookService webhookService = mock(GithubWebhookService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new GithubWebhookController(
                    new WebhookSignatureVerifier(new WebhookProperties(SECRET)),
                    webhookService,
                    JsonMapper.builder().build()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private org.springframework.test.web.servlet.ResultActions send(String event, String body, String signature)
            throws Exception {
        var request = post(GithubWebhookController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(GithubWebhookController.EVENT_HEADER, event)
                .content(body.getBytes(StandardCharsets.UTF_8));
        if (signature != null) {
            request.header(WebhookSignatureVerifier.HEADER, signature);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("서명이 틀리면 401 INVALID_WEBHOOK_SIGNATURE, 서비스는 호출하지 않는다")
    void invalidSignature() throws Exception {
        send("push", PUSH_BODY, "sha256=deadbeef")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));

        verifyNoInteractions(webhookService);
    }

    @Test
    @DisplayName("서명 헤더가 없으면 401")
    void missingSignature() throws Exception {
        send("push", PUSH_BODY, null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ping 이벤트는 200")
    void ping() throws Exception {
        String body = "{\"zen\":\"Keep it logically awesome.\"}";

        send("ping", body, WebhookSignatureVerifierTest.sign(SECRET, body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        verifyNoInteractions(webhookService);
    }

    @Test
    @DisplayName("올바른 push 는 202, 모르는 필드가 있어도 파싱해서 서비스에 넘긴다")
    void push() throws Exception {
        when(webhookService.handlePush(any(GithubPushPayload.class)))
                .thenReturn(new WebhookResponse(List.of(1L), List.of(), null));

        send("push", PUSH_BODY, WebhookSignatureVerifierTest.sign(SECRET, PUSH_BODY.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.redeployed[0]").value(1));

        verify(webhookService).handlePush(new GithubPushPayload(
                "refs/heads/main", "abc123", false,
                new GithubPushPayload.Repository("https://github.com/1ub7/demo.git", "main")));
    }
}
