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
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "API 키가 없거나 올바르지 않습니다."),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "Webhook 서명이 올바르지 않습니다."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "다른 요청과 동시에 수정되었습니다. 다시 시도해주세요."),
    // App
    APP_NOT_FOUND(HttpStatus.NOT_FOUND, "앱을 찾을 수 없습니다."),
    DUPLICATE_APP_NAME(HttpStatus.CONFLICT, "이미 사용 중인 앱 이름입니다."),
    INVALID_APP_STATUS(HttpStatus.CONFLICT, "현재 상태에서 수행할 수 없는 작업입니다."),
    PORT_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "할당 가능한 포트가 없습니다."),
    DOCKERFILE_NOT_FOUND(HttpStatus.BAD_REQUEST, "저장소에 Dockerfile이 없습니다."),
    PORT_ALLOCATION_CONFLICT(HttpStatus.CONFLICT, "포트 할당이 충돌했습니다. 잠시 후 다시 시도해주세요."),
    DEPLOY_QUEUE_FULL(HttpStatus.SERVICE_UNAVAILABLE, "배포 대기열이 가득 찼습니다. 잠시 후 다시 시도해주세요."),
    APP_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "실행 중인 앱이 최대 개수에 도달했습니다. 사용하지 않는 앱을 중지하거나 삭제해주세요."),
    // Account
    NOT_ALLOWED_ACCOUNT(HttpStatus.FORBIDDEN, "허용되지 않은 GitHub 계정입니다."),
    INVALID_REPOSITORY_URL(HttpStatus.BAD_REQUEST, "올바른 GitHub 저장소 주소가 아닙니다."),
    // Infra
    GIT_CLONE_FAILED(HttpStatus.BAD_REQUEST, "저장소를 복제할 수 없습니다."),
    IMAGE_BUILD_FAILED(HttpStatus.BAD_REQUEST, "이미지 빌드에 실패했습니다."),
    CONTAINER_RUN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "컨테이너 실행에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
