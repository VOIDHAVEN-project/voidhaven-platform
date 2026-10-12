package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.HomepageAdministrationService;
import cn.voidhaven.api.service.HomepageProjectionService;
import cn.voidhaven.api.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/homepage-placements")
public class HomepageAdminController {
    record ReplaceRequest(@NotNull @Size(max = 48) List<HomepageAdministrationService.PlacementInput> placements) { }
    private final HomepageAdministrationService homepage;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    public HomepageAdminController(HomepageAdministrationService homepage, IdempotencyService idempotency, AccountRepository accounts) {
        this.homepage = homepage;
        this.idempotency = idempotency;
        this.accounts = accounts;
    }

    @GetMapping
    ApiResponse<HomepageAdministrationService.PlacementReplacement> list(HttpServletRequest request) {
        return ok(homepage.current(), request);
    }

    @GetMapping("/candidates")
    ApiResponse<HomepageAdministrationService.CandidatePage> candidates(
            @RequestParam(required = false) String slot,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        return ok(homepage.candidates(slot, query, cursor), request);
    }

    @PostMapping("/preview")
    ApiResponse<HomepageProjectionService.HomeSections> preview(@Valid @RequestBody ReplaceRequest body, HttpServletRequest request) {
        return ok(homepage.preview(body.placements()), request);
    }

    @PutMapping
    ApiResponse<HomepageAdministrationService.PlacementReplacement> replace(
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("If-Match") String version,
            @Valid @RequestBody ReplaceRequest body,
            Authentication auth,
            HttpServletRequest request) {
        return ok(idempotency.execute(key, body, HomepageAdministrationService.PlacementReplacement.class,
                () -> homepage.replace(body.placements(), parseVersion(version), accountId(auth), role(auth), requestId(request))), request);
    }

    private long parseVersion(String value) {
        try {
            return Long.parseLong(value.replace("\"", ""));
        } catch (Exception error) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "HOMEPAGE_VERSION_INVALID", "首页编排版本无效。");
        }
    }

    private String accountId(Authentication auth) { return accounts.findByLoginId(auth.getName()).orElseThrow().getId(); }
    private String role(Authentication auth) { return auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority())) ? "SUPER_ADMIN" : "ADMIN"; }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
    private <T> ApiResponse<T> ok(T data, HttpServletRequest request) { return new ApiResponse<>(data, requestId(request)); }
}
