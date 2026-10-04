package Depolita.account.exception;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;

public class InvalidRepositoryUrlException extends CustomException {
    public InvalidRepositoryUrlException() {
        super(ErrorCode.INVALID_APP_STATUS);
    }
}
