package Depolita.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

class DiscordNotifierTest {
    private static final String URL = "https://discord.com/api/webhooks/1/token";

    @Test
    @DisplayName("Webhook URL로 {\"content\": ...} JSON을 POST 한다")
    void postsContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"content\":\"hello\"}"))
                .andRespond(withNoContent());

        new DiscordNotifier(builder.build(), URL).send("hello");

        server.verify();
    }

    @Test
    @DisplayName("Discord 메시지 길이 제한(2000자)을 넘으면 자른다")
    void truncatesLongMessage() {
        String truncated = DiscordNotifier.truncate("x".repeat(5000));

        assertEquals(DiscordNotifier.MAX_LENGTH, truncated.length());
    }
}
