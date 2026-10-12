package cn.voidhaven.api.web;

import cn.voidhaven.api.service.*;
import cn.voidhaven.api.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/partner-profiles")
public class OrganizationAdministrationController {
    private final OrganizationAdministrationService organizations;
    private final IdempotencyService idempotency;
    public OrganizationAdministrationController(OrganizationAdministrationService organizations, IdempotencyService idempotency) { this.organizations = organizations; this.idempotency = idempotency; }
    record Quota(@Min(0) @Max(1000) int quota, @NotBlank @Size(max = 1000) String reason) { }
    record Reason(@NotBlank @Size(max = 1000) String reason) { }
    @PatchMapping("/{id}/member-quota")
    ApiResponse<OrganizationAdministrationService.State> quota(@PathVariable String id, @RequestHeader("If-Match") String match, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Quota body, Authentication auth, HttpServletRequest request) {
        long version = version(match);
        return ok(idempotency.execute(key, Map.of("id", id, "version", version, "quota", body.quota(), "reason", body.reason()), OrganizationAdministrationService.State.class,
                () -> organizations.quota(id, body.quota(), version, body.reason(), actor(auth), role(auth), requestId(request))), request);
    }
    @PostMapping("/{id}/suspend")
    ApiResponse<OrganizationAdministrationService.State> suspend(@PathVariable String id, @RequestHeader("If-Match") String match, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) { return change(id, false, match, key, body, auth, request); }
    @PostMapping("/{id}/reactivate")
    ApiResponse<OrganizationAdministrationService.State> reactivate(@PathVariable String id, @RequestHeader("If-Match") String match, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) { return change(id, true, match, key, body, auth, request); }
    private ApiResponse<OrganizationAdministrationService.State> change(String id, boolean active, String match, String key, Reason body, Authentication auth, HttpServletRequest request) {
        long version = version(match);
        return ok(idempotency.execute(key, Map.of("id", id, "active", active, "version", version, "reason", body.reason()), OrganizationAdministrationService.State.class,
                () -> organizations.changeActive(id, active, version, body.reason(), actor(auth), role(auth), requestId(request))), request);
    }
    private long version(String value) { try { return Long.parseLong(value.replace("\"", "")); } catch (NumberFormatException error) { throw new ApiException(HttpStatus.BAD_REQUEST, "ORGANIZATION_VERSION_REQUIRED", "请携带有效的组织版本。"); } }
    private String actor(Authentication auth) { return ((AccountPrincipal) auth.getPrincipal()).accountId(); }
    private String role(Authentication auth) { return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")) ? "SUPER_ADMIN" : "ADMIN"; }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
    private <T> ApiResponse<T> ok(T value, HttpServletRequest request) { return new ApiResponse<>(value, requestId(request)); }
}
