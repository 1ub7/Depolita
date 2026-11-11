package Depolita.domain.account.exception;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;

public class InvalidRepositoryUrlException extends CustomException {
    public InvalidRepositoryUrlException() {
        super(ErrorCode.INVALID_REPOSITORY_URL);
    }
}
