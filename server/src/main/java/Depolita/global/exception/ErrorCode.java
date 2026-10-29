package Depolita.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    // App
    APP_NOT_FOUND(HttpStatus.NOT_FOUND, "앱을 찾을 수 없습니다."),
    DUPLICATE_APP_NAME(HttpStatus.CONFLICT, "이미 사용 중인 앱 이름입니다."),
    INVALID_APP_STATUS(HttpStatus.CONFLICT, "현재 상태에서 수행할 수 없는 작업입니다."),
    PORT_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "할당 가능한 포트가 없습니다."),
    DOCKERFILE_NOT_FOUND(HttpStatus.BAD_REQUEST, "저장소에 Dockerfile이 없습니다."),
    // Account
    NOT_ALLOWED_ACCOUNT(HttpStatus.FORBIDDEN, "허용되지 않은 GitHub 계정입니다.");

    private final HttpStatus status;
    private final String message;
}
