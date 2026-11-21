package Depolita.global.security;

import Depolita.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ForwardedHeaderTest {
    private static final String CLIENT_IP = "203.0.113.7";

    @LocalServerPort
    int port;

    @Test
    @DisplayName("신뢰하는 프록시가 보낸 X-Forwarded-For → 인증 실패 로그에 실제 클라이언트 IP")
    void logsForwardedClientIp(CapturedOutput output) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/apps"))
                        .header("X-Forwarded-For", CLIENT_IP)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertTrue(output.getOut().contains("from " + CLIENT_IP),
                "인증 실패 로그에 X-Forwarded-For의 IP가 찍혀야 한다");
    }
}