package Depolita.domain.app.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppStatus {
    CLONING("저장소 복제 중"),
    BUILDING("이미지 빌드 중"),
    RUNNING("실행 중"),
    STOPPED("중지됨"),
    FAILED("실패");

    private final String description;
    public boolean isInProgress() {
        return this == CLONING || this == BUILDING;
    }

    public boolean canTransitionTo(AppStatus next) {
        return switch (this) {
            case CLONING -> next == BUILDING || next == FAILED;
            case BUILDING -> next == RUNNING || next == FAILED;
            case RUNNING -> next == STOPPED || next == FAILED;
            case STOPPED, FAILED -> false;
        };
    }
}
