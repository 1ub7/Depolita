package Depolita.domain.app.exception;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;

public class DockerfileNotFoundException extends CustomException {
    public DockerfileNotFoundException() {
        super(ErrorCode.DOCKERFILE_NOT_FOUND);
    }
}
