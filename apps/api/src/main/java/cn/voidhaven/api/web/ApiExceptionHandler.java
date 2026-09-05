package cn.voidhaven.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), "填写内容不符合要求。"));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请检查填写内容后重试。", fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintValidation(ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(error -> fields.put(error.getPropertyPath().toString(), "填写内容不符合要求。"));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请检查填写内容后重试。", fields, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException exception, HttpServletRequest request) {
        String code = "Idempotency-Key".equalsIgnoreCase(exception.getHeaderName()) ? "IDEMPOTENCY_KEY_REQUIRED" : "REQUEST_HEADER_REQUIRED";
        String message = "IDEMPOTENCY_KEY_REQUIRED".equals(code) ? "请携带有效的幂等请求标识后重试。" : "请求缺少必要的安全标识。";
        return response(HttpStatus.BAD_REQUEST, code, message, Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API failure requestId={} method={} path={}", requestId(request), request.getMethod(), request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "TEMPORARILY_UNAVAILABLE", "服务暂时不可用，请稍后重试。", Map.of(), request);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, Map<String, String> fields, HttpServletRequest request) {
        boolean retryable = status == HttpStatus.TOO_MANY_REQUESTS || status.is5xxServerError();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (status == HttpStatus.TOO_MANY_REQUESTS) builder.header(HttpHeaders.RETRY_AFTER, "900");
        return builder.body(new ApiError(code, message, requestId(request), fields, retryable));
    }

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return id == null ? "" : id.toString();
    }
}
