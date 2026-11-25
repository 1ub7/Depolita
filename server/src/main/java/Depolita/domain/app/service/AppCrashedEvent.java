package Depolita.domain.app.service;

/** 실행 중이던 앱이 죽었다 (OOM, 크래시 루프, 외부에서 삭제 등). 재조정 루프가 발견해서 발행한다. */
public record AppCrashedEvent(Long appId, String appName, String reason) {
}
