package Depolita.global.security;

import Depolita.global.config.DepolitaProperties;
import Depolita.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Slf4j
@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-API-Key";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness"
    );

    private final byte[] expectedKey;

    public ApiKeyFilter(DepolitaProperties properties) {
        this.expectedKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
        if (expectedKey.length == 0) {
            log.warn("[auth] DEPOLITA_API_KEY가 설정되지 않았습니다. 모든 API 요청이 거절됩니다.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isValid(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("[auth] 인증 실패 {} {} from {}",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        writeUnauthorized(response);
    }

    private boolean isValid(String provided) {
        if (expectedKey.length == 0 || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"title":"%s","status":%d,"detail":"%s","code":"%s"}"""
                .formatted(errorCode.name(), errorCode.getStatus().value(),
                        errorCode.getMessage(), errorCode.name()));
    }
}
