package Depolita.domain.app.exception;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;

public class InvalidAppStatusException extends CustomException {
    public InvalidAppStatusException() {
        super(ErrorCode.INVALID_APP_STATUS);
    }
}
