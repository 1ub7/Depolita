package Depolita.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(CustomException.class)
    public ProblemDetail handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[{}] {}", errorCode.name(), e.getMessage());
        return createProblemDetail(errorCode, e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException e) {
        ErrorCode errorCode = ErrorCode.CONCURRENT_MODIFICATION;
        log.warn("[{}] {}", errorCode.name(), e.getMessage());
        return createProblemDetail(errorCode, errorCode.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "reason", Objects.requireNonNullElse(fe.getDefaultMessage(), "")))
                .toList();
        return invalidInput(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), ""),
                                "reason", Objects.requireNonNullElse(error.getDefaultMessage(), ""))))
                .toList();
        return invalidInput(errors);
    }

    private ResponseEntity<Object> invalidInput(List<Map<String, String>> errors) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        log.warn("[{}] validation failed: {}", errorCode.name(), errors);
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
        pd.setProperty("code", errorCode.name());
        return pd;
    }
}