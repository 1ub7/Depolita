package Depolita.domain.app.exception;

import Depolita.global.exception.CustomException;
import Depolita.global.exception.ErrorCode;

public class PortExhaustedException extends CustomException {
    public PortExhaustedException() {
        super(ErrorCode.PORT_EXHAUSTED);
    }
}
