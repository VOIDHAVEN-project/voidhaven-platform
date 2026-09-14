package cn.voidhaven.api.web;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.ContentSourcePolicy;
import cn.voidhaven.api.service.PublicCaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/v1/public")
public class PublicInputController {
    private final PublicCaseService cases; private final ContentSourcePolicy sources;
    public PublicInputController(PublicCaseService cases, ContentSourcePolicy sources) { this.cases = cases; this.sources = sources; }
    record SubmissionRequest(@NotNull PublicCaseService.SubmissionType submissionType, @NotBlank @Size(max = 240) String title, @NotBlank @Size(max = 5000) String message, @Size(max = 2048) String linkUrl, @Size(max = 1000) String imageDescription, @Size(max = 500) String contact) { }
    record PartnerApplicationRequest(@NotBlank @Size(max = 120) String displayName, @NotBlank @Size(max = 5000) String introduction, @Size(max = 2048) String portfolioUrl, @NotBlank @Size(max = 500) String contact) { }
    record CaseQuery(@NotBlank @Size(min = 26, max = 26) String id, @NotBlank @Size(min = 16, max = 256) String queryCode) { }
    @PostMapping("/submissions") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PublicCaseService.CreatedCase> submission(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody SubmissionRequest body, HttpServletRequest request) {
        requireSafeUrl(body.linkUrl());
        PublicCaseService.CreatedCase created = cases.createSubmission(key, new PublicCaseService.SubmissionCommand(body.submissionType(), body.title(), body.message(), body.linkUrl(), body.imageDescription(), body.contact()));
        return new ApiResponse<>(created, requestId(request));
    }
    @PostMapping("/submissions/status-query")
    ApiResponse<PublicCaseService.CaseStatus> submissionStatus(@Valid @RequestBody CaseQuery body, HttpServletRequest request) { return new ApiResponse<>(cases.submissionStatus(body.id(), body.queryCode()), requestId(request)); }
    @PostMapping("/partner-applications") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<PublicCaseService.CreatedCase> partnerApplication(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PartnerApplicationRequest body, HttpServletRequest request) {
        requireSafeUrl(body.portfolioUrl());
        PublicCaseService.CreatedCase created = cases.createPartnerApplication(key, new PublicCaseService.PartnerApplicationCommand(body.displayName(), body.introduction(), body.portfolioUrl(), body.contact()));
        return new ApiResponse<>(created, requestId(request));
    }
    @PostMapping("/partner-applications/status-query")
    ApiResponse<PublicCaseService.CaseStatus> partnerStatus(@Valid @RequestBody CaseQuery body, HttpServletRequest request) { return new ApiResponse<>(cases.partnerApplicationStatus(body.id(), body.queryCode()), requestId(request)); }
    private void requireSafeUrl(String value) { if (value != null && !value.isBlank()) sources.requireAllowed(value); }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
