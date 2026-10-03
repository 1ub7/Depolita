package Depolita.account.exception;

public class InvalidRepositoryUrlException extends RuntimeException {
    public InvalidRepositoryUrlException() {
        super("올바른 GitHub 저장소 주소가 아닙니다.");
    }
}
