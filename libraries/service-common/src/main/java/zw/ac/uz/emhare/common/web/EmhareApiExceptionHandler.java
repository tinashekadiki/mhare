package zw.ac.uz.emhare.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class EmhareApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EmhareApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalStateException.class, OptimisticLockingFailureException.class})
    ProblemDetail handleBusinessConflict(RuntimeException exception, HttpServletRequest request) {
        String detail = exception instanceof OptimisticLockingFailureException
                ? "The record was changed by another user. Refresh it and retry the operation."
                : exception.getMessage();
        return problem(HttpStatus.CONFLICT, "Operation not allowed", detail, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "You do not have permission to perform this operation.",
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request fields are invalid.",
                request);
        List<Map<String, String>> violations = exception.getBindingResult().getAllErrors().stream()
                .map(error -> Map.of(
                        "field", error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        "message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList();
        problemDetail.setProperty("violations", violations);
        return problemDetail;
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    ProblemDetail handleConstraintViolation(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request values are invalid.",
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "The request body could not be read. Check value types and JSON syntax.",
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Database constraint rejected request correlationId={}", correlationId(), exception);
        return problem(
                HttpStatus.CONFLICT,
                "Data conflict",
                "The operation conflicts with an existing record or business constraint.",
                request);
    }

    @ExceptionHandler(ServiceDependencyUnavailableException.class)
    ProblemDetail handleServiceDependencyUnavailable(
            ServiceDependencyUnavailableException exception,
            HttpServletRequest request) {
        log.warn("Required service dependency unavailable correlationId={}", correlationId(), exception);
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service temporarily unavailable",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpectedFailure(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API failure correlationId={}", correlationId(), exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "The operation could not be completed. Use the correlation ID when contacting support.",
                request);
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                status,
                detail == null || detail.isBlank() ? title : detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create("https://emhare.uz.ac.zw/problems/" + status.value()));
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("correlationId", correlationId());
        return problemDetail;
    }

    private String correlationId() {
        String correlationId = MDC.get("correlationId");
        return correlationId == null ? "unavailable" : correlationId;
    }
}
