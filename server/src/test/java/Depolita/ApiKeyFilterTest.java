package Depolita;

import Depolita.global.config.DepolitaProperties;
import Depolita.global.security.ApiKeyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiKeyFilterTest {

    private MockHttpServletResponse call(String configuredKey, String path, String headerValue, MockFilterChain chain)
            throws Exception {
        ApiKeyFilter filter = new ApiKeyFilter(new DepolitaProperties(configuredKey, null));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (headerValue != null) {
            request.addHeader(ApiKeyFilter.HEADER, headerValue);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("올바른 키 → 통과")
    void validKey() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = call("secret", "/api/apps", "secret", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest()); // 다음 필터로 넘어감
    }

    @Test
    @DisplayName("틀린 키 → 401")
    void wrongKey() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = call("secret", "/api/apps", "guess", chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("헤더 없음 → 401")
    void missingKey() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertEquals(401, call("secret", "/api/apps/1/stop", null, chain).getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("서버에 키가 설정되지 않았으면 빈 헤더로도 통과할 수 없다 (fail-closed)")
    void notConfigured() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertEquals(401, call("", "/api/apps", "", chain).getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("헬스체크는 키 없이 통과")
    void healthIsPublic() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertEquals(200, call("secret", "/actuator/health", null, chain).getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("GitHub Webhook 은 키 없이 통과 (서명 검증은 컨트롤러에서)")
    void webhookIsPublic() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertEquals(200, call("secret", "/api/webhooks/github", null, chain).getStatus());
        assertNotNull(chain.getRequest());
    }
}
