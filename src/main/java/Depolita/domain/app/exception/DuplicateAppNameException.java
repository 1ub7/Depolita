package Depolita.domain.app.exception;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;

public class DuplicateAppNameException extends CustomException {
    public DuplicateAppNameException() {
        super(ErrorCode.DUPLICATE_APP_NAME);
    }
}
