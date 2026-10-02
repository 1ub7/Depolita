package Depolita.global.exception;

import ch.qos.logback.core.spi.ErrorCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(CustomException.class)
    public ProblemDetail handleCustomException(CustomException e) {
        ErrorCodes errorCodes = new ErrorCodes();
        log.warn("[{}] {}", errorCodes.getCode(), e.getMessage());
        return createProblemDetail(errorCodes, e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "reason", Objects.requireNonNullElse(fe.getDefaultMessage(), "")))
                .toList();
        log.warn("[{}] validation failed: {}", errorCode.getCode(), errors);
        ProblemDetail pd = createProblemDetail(errorCode, errorCode.getMessage());
        pd.setProperty("errors", errors);
        return ResponseEntity.status(errorCode.getStatus()).body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return createProblemDetail(errorCode, errorCode.getMessage());
    }

    private ProblemDetail createProblemDetail(ErrorCode errorCode, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), detail);
        pd.setTitle(errorCode.name());
        pd.setProperty("code", errorCode.getCode());
        return pd;
    }
}
