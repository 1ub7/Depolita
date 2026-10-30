package Depolita.domain.account.exception;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;

public class NotAllowedAccountException extends CustomException {
    public NotAllowedAccountException() {
        super(ErrorCode.NOT_ALLOWED_ACCOUNT);
    }
}
