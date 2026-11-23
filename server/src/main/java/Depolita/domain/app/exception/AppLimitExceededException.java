package Depolita.domain.app.exception;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;

public class AppLimitExceededException extends CustomException {
    public AppLimitExceededException() {
        super(ErrorCode.APP_LIMIT_EXCEEDED);
    }
}
