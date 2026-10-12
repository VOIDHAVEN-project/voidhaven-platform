package cn.voidhaven.api.web;
import cn.voidhaven.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAuthorizationController {
    private final AdminAuthorizationService authorizations;
    private final IdempotencyService idempotency;
    public AdminAuthorizationController(AdminAuthorizationService authorizations, IdempotencyService idempotency) { this.authorizations = authorizations; this.idempotency = idempotency; }
    @PostMapping("/accounts/authorization-preview") ApiResponse<?> previewNew(@RequestBody AdminAuthorizationService.Input input, HttpServletRequest request) { return ok(authorizations.previewNew(input), request); }
    @GetMapping("/permission-catalog") ApiResponse<?> catalog(HttpServletRequest request) { return ok(AdminAuthorizationService.CATALOG, request); }
    @GetMapping("/accounts/{id}/authorization") ApiResponse<?> current(@PathVariable String id, HttpServletRequest request) { authorizations.target(id); return ok(authorizations.current(id), request); }
    @PostMapping("/accounts/{id}/authorization/preview") ApiResponse<?> preview(@PathVariable String id, @RequestBody AdminAuthorizationService.Input input, HttpServletRequest request) { return ok(authorizations.preview(id, input), request); }
    @PutMapping("/accounts/{id}/authorization") ApiResponse<?> replace(@PathVariable String id, @RequestHeader("If-Match") String version, @RequestHeader("Idempotency-Key") String key, @RequestBody AdminAuthorizationService.Input input, HttpServletRequest request) {
        long expected = version(version); authorizations.preview(id, input);
        return ok(idempotency.execute(key, Map.of("id", id, "version", expected, "input", input), AdminAuthorizationService.Authorization.class,
                () -> authorizations.replace(id, expected, input, requestId(request))), request);
    }
    @PostMapping("/accounts/{id}/authorization/revoke") ApiResponse<?> revoke(@PathVariable String id, @RequestHeader("If-Match") String version, @RequestHeader("Idempotency-Key") String key, @RequestBody AdminController.ActionReason reason, HttpServletRequest request) {
        var input = new AdminAuthorizationService.Input(List.of(), true, null, reason.reason()); long expected = version(version);
        return ok(idempotency.execute(key, Map.of("id", id, "version", expected, "input", input), AdminAuthorizationService.Authorization.class,
                () -> authorizations.replace(id, expected, input, requestId(request))), request);
    }
    @GetMapping("/accounts/{id}/authorization/history") ApiResponse<?> history(@PathVariable String id, HttpServletRequest request) { return ok(authorizations.history(id), request); }
    private long version(String value) { try { return Long.parseLong(value.replace("\"", "")); } catch (NumberFormatException error) { throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "AUTHORIZATION_VERSION_REQUIRED", "请提供授权版本。"); } }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
    private ApiResponse<?> ok(Object value, HttpServletRequest request) { return new ApiResponse<>(value, requestId(request)); }
}
