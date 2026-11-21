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
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.tomcat.remoteip.internal-proxies=10.255.255.254/32"
)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class UntrustedForwardedHeaderTest {
    private static final String SPOOFED_IP = "203.0.113.7";

    @LocalServerPort
    int port;

    @Test
    @DisplayName("신뢰하지 않는 곳에서 온 위조 X-Forwarded-For는 무시된다")
    void ignoresSpoofedHeader(CapturedOutput output) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/apps"))
                        .header("X-Forwarded-For", SPOOFED_IP)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertFalse(output.getOut().contains("from " + SPOOFED_IP),
                "위조한 IP가 로그에 찍히면 안 된다");
    }
}