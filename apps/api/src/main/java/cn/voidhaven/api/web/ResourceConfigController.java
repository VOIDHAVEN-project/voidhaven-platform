package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.ResourceConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/resource-config")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class ResourceConfigController {
    private final ResourceConfigService service;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    public ResourceConfigController(ResourceConfigService service, IdempotencyService idempotency, AccountRepository accounts) {
        this.service = service; this.idempotency = idempotency; this.accounts = accounts;
    }
    @GetMapping ApiResponse<ResourceConfigService.Config> current(HttpServletRequest request) { return ok(service.current(), request); }
    @GetMapping("/versions") ApiResponse<List<ResourceConfigService.Config>> history(@RequestParam(required = false) Long before, HttpServletRequest request) {
        return ok(service.history(before), request);
    }
    @PutMapping ApiResponse<ResourceConfigService.Config> replace(@RequestHeader("Idempotency-Key") String key,
            @RequestHeader("If-Match") String version, @RequestBody ResourceConfigService.Config input,
            Authentication auth, HttpServletRequest request) {
        long expected;
        try { expected = Long.parseLong(version.replace("\"", "")); }
        catch (NumberFormatException e) { throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "RESOURCE_CONFIG_VERSION_INVALID", "配置版本无效。"); }
        String actor = accounts.findByLoginId(auth.getName()).orElseThrow().getId();
        String role = auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority())) ? "SUPER_ADMIN" : "ADMIN";
        record Write(ResourceConfigService.Config config, long version) { }
        return ok(idempotency.execute(key, new Write(input, expected), ResourceConfigService.Config.class,
                () -> service.replace(input, expected, actor, role, requestId(request))), request);
    }
    private String requestId(HttpServletRequest r) { return (String) r.getAttribute(RequestIdFilter.ATTRIBUTE); }
    private <T> ApiResponse<T> ok(T value, HttpServletRequest r) { return new ApiResponse<>(value, requestId(r)); }
}
