package cn.voidhaven.api.web;

import cn.voidhaven.api.service.ContentVerificationService;
import cn.voidhaven.api.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/content-items/{id}/verifications")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class ContentVerificationAdminController {
    record VerificationRequest(@NotBlank @Pattern(regexp = "VALID|INVALID|NEEDS_REVIEW") String outcome,
                               @Size(max = 1000) String detailSummary, Instant nextCheckAt) { }

    private final ContentVerificationService verifications;
    private final IdempotencyService idempotency;

    public ContentVerificationAdminController(ContentVerificationService verifications, IdempotencyService idempotency) {
        this.verifications = verifications;
        this.idempotency = idempotency;
    }

    @GetMapping
    ApiResponse<List<ContentVerificationService.Verification>> history(@PathVariable String id,
                                                                       HttpServletRequest request) {
        return new ApiResponse<>(verifications.history(id), requestId(request));
    }

    @PostMapping
    ApiResponse<ContentVerificationService.Verification> record(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody VerificationRequest body,
            Authentication auth, HttpServletRequest request) {
        var result = idempotency.execute(key, Map.of("contentId", id, "outcome", body.outcome(),
                        "detailSummary", body.detailSummary() == null ? "" : body.detailSummary(),
                        "nextCheckAt", body.nextCheckAt() == null ? "" : body.nextCheckAt().toString()),
                ContentVerificationService.Verification.class,
                () -> verifications.record(id, body.outcome(), body.detailSummary(), body.nextCheckAt(),
                        auth.getName(), requestId(request)));
        return new ApiResponse<>(result, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
