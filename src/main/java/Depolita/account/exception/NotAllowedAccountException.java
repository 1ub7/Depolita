package Depolita.account.exception;

public class NotAllowedAccountException extends RuntimeException {
    public NotAllowedAccountException() {
        super("허용되지 않은 계정의 저장소입니다.");
    }
}
