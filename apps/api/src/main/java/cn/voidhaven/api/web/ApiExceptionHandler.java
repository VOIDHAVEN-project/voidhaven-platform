package cn.voidhaven.api.web;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) { return response(ex.status(), ex.code(), ex.getMessage(), request); }
    @ExceptionHandler({ConstraintViolationException.class, org.springframework.web.bind.MethodArgumentNotValidException.class})
    ResponseEntity<ApiError> handleValidation(Exception ex, HttpServletRequest request) { return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请检查填写内容后重试。", request); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) { return response(HttpStatus.INTERNAL_SERVER_ERROR, "TEMPORARILY_UNAVAILABLE", "服务暂时不可用，请稍后重试。", request); }
    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.ATTRIBUTE); return ResponseEntity.status(status).body(new ApiError(code, message, id == null ? "" : id.toString()));
    }
}
