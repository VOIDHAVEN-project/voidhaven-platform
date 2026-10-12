package cn.voidhaven.api.web;

import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.CatalogOrderService;
import cn.voidhaven.api.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/catalog-order")
public class CatalogOrderAdminController {
    record ReplaceRequest(
            @NotNull String defaultSort,
            @NotNull @Size(max = 100) List<CatalogOrderService.EntryInput> entries) { }

    private final CatalogOrderService catalog;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;

    public CatalogOrderAdminController(CatalogOrderService catalog, IdempotencyService idempotency, AccountRepository accounts) {
        this.catalog = catalog;
        this.idempotency = idempotency;
        this.accounts = accounts;
    }

    @GetMapping("/{kind}")
    ApiResponse<CatalogOrderService.Replacement> current(@PathVariable String kind, HttpServletRequest request) {
        return ok(catalog.current(parseKind(kind)), request);
    }

    @GetMapping("/{kind}/candidates")
    ApiResponse<CatalogOrderService.CandidatePage> candidates(
            @PathVariable String kind,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        return ok(catalog.candidates(parseKind(kind), query, cursor), request);
    }

    @PutMapping("/{kind}")
    ApiResponse<CatalogOrderService.Replacement> replace(
            @PathVariable String kind,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("If-Match") String version,
            @Valid @RequestBody ReplaceRequest body,
            Authentication auth,
            HttpServletRequest request) {
        ContentKind contentKind = parseKind(kind);
        return ok(idempotency.execute(key, body, CatalogOrderService.Replacement.class,
                () -> catalog.replace(contentKind, body.defaultSort(), body.entries(), parseVersion(version), accountId(auth), role(auth), requestId(request))), request);
    }

    private ContentKind parseKind(String kind) {
        try {
            return ContentKind.valueOf(kind);
        } catch (Exception error) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "CATALOG_KIND_INVALID", "目录类型无效。");
        }
    }

    private long parseVersion(String value) {
        try {
            return Long.parseLong(value.replace("\"", ""));
        } catch (Exception error) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "CATALOG_VERSION_INVALID", "目录排序版本无效。");
        }
    }

    private String accountId(Authentication auth) { return accounts.findByLoginId(auth.getName()).orElseThrow().getId(); }
    private String role(Authentication auth) { return auth.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority())) ? "SUPER_ADMIN" : "ADMIN"; }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
    private <T> ApiResponse<T> ok(T data, HttpServletRequest request) { return new ApiResponse<>(data, requestId(request)); }
}
