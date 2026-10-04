package Depolita.domain.app.exception;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;

public class AppNotFoundException extends CustomException {
    public AppNotFoundException() {
        super(ErrorCode.APP_NOT_FOUND);
    }
}
