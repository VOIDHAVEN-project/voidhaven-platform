package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.ProjectJoinService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/participation/project-join")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ProjectJoinAdminController {
    record EntryRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description,
                        @NotBlank @Pattern(regexp = "LINK|QR_CODE|TEXT") String presentationType,
                        @NotBlank @Pattern(regexp = "URL|EMAIL|WECHAT|QQ|PHONE|OTHER") String contactChannel,
                        @NotBlank @Pattern(regexp = "PUBLIC|COMMUNITY_GATED") String accessLevel,
                        @Size(max = 2048) String value, @Size(max = 26) String mediaAssetId,
                        boolean publicExposureConfirmed, @Min(0) @Max(1000) int sortOrder) { }
    record Reason(@NotBlank @Size(max = 1000) String reason) { }

    private final ProjectJoinService projectJoin;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;

    public ProjectJoinAdminController(ProjectJoinService projectJoin, IdempotencyService idempotency,
                                      AccountRepository accounts) {
        this.projectJoin = projectJoin;
        this.idempotency = idempotency;
        this.accounts = accounts;
    }

    @GetMapping
    ApiResponse<List<ProjectJoinService.EntrySummary>> entries(HttpServletRequest request) {
        return ok(projectJoin.entries(), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ProjectJoinService.EntrySummary> createEntry(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody EntryRequest body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, body, ProjectJoinService.EntrySummary.class,
                () -> projectJoin.createEntry(body.name(), body.description(), body.presentationType(),
                        body.contactChannel(), body.accessLevel(), body.value(), body.mediaAssetId(),
                        body.publicExposureConfirmed(), body.sortOrder(), accountId(auth), "SUPER_ADMIN",
                        requestId(request))), request);
    }

    @PatchMapping("/{id}")
    ApiResponse<ProjectJoinService.EntrySummary> updateEntry(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader("If-Match") String version,
            @Valid @RequestBody EntryRequest body, Authentication auth, HttpServletRequest request) {
        long expected = version(version);
        return ok(idempotency.execute(key, Map.of("id", id, "version", expected, "body", body),
                ProjectJoinService.EntrySummary.class,
                () -> projectJoin.updateEntry(id, expected, body.name(), body.description(),
                        body.presentationType(), body.contactChannel(), body.accessLevel(), body.value(),
                        body.mediaAssetId(), body.publicExposureConfirmed(), body.sortOrder(), accountId(auth),
                        "SUPER_ADMIN", requestId(request))), request);
    }

    @PostMapping("/{id}/deactivate")
    ApiResponse<ProjectJoinService.EntrySummary> deactivateEntry(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader("If-Match") String version,
            @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) {
        long expected = version(version);
        return ok(idempotency.execute(key, Map.of("id", id, "version", expected, "reason", body.reason()),
                ProjectJoinService.EntrySummary.class,
                () -> projectJoin.deactivateEntry(id, expected, body.reason(), accountId(auth), "SUPER_ADMIN",
                        requestId(request))), request);
    }

    private String accountId(Authentication auth) {
        return accounts.findByLoginId(auth.getName()).orElseThrow().getId();
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }

    private <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return new ApiResponse<>(data, requestId(request));
    }

    private long version(String value) {
        try {
            return Long.parseLong(value.replace("\"", ""));
        } catch (NumberFormatException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_JOIN_VERSION_REQUIRED",
                    "请携带有效的项目组入口版本后重试。");
        }
    }
}
