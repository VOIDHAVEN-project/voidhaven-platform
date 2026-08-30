package cn.voidhaven.api.web;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.support.Ids;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/v1/public")
public class PublicInputController {
    private final JdbcTemplate jdbc; private final IdempotencyService idempotency;
    public PublicInputController(JdbcTemplate jdbc, IdempotencyService idempotency) { this.jdbc = jdbc; this.idempotency = idempotency; }
    record SubmissionRequest(@NotNull SubmissionType submissionType, @NotBlank @Size(max = 240) String title, @NotBlank @Size(max = 5000) String message, @Size(max = 2048) String linkUrl, @Size(max = 1000) String imageDescription) { }
    enum SubmissionType { CONTENT, FEEDBACK, COPYRIGHT }
    record PartnerApplicationRequest(@NotBlank @Size(max = 120) String displayName, @NotBlank @Size(max = 5000) String introduction, @Size(max = 2048) String portfolioUrl) { }
    record Created(String id) { }
    @PostMapping("/submissions") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Created> submission(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody SubmissionRequest body, HttpServletRequest request) {
        Created created = idempotency.execute(key, body, Created.class, () -> { String id = Ids.ulid(); jdbc.update("insert into content_submissions (id, submission_type, title, message, link_url, image_description) values (?, ?, ?, ?, ?, ?)", id, body.submissionType().name(), body.title(), body.message(), blankToNull(body.linkUrl()), blankToNull(body.imageDescription())); return new Created(id); }); return new ApiResponse<>(created, requestId(request));
    }
    @PostMapping("/partner-applications") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<Created> partnerApplication(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PartnerApplicationRequest body, HttpServletRequest request) {
        Created created = idempotency.execute(key, body, Created.class, () -> { String id = Ids.ulid(); jdbc.update("insert into partner_applications (id, display_name, introduction, portfolio_url) values (?, ?, ?, ?)", id, body.displayName(), body.introduction(), blankToNull(body.portfolioUrl())); return new Created(id); }); return new ApiResponse<>(created, requestId(request));
    }
    @PostMapping("/community-gate/verify")
    ApiResponse<Map<String, String>> communityGate(HttpServletRequest request) { return new ApiResponse<>(Map.of("outcome", "NOT_CONFIGURED", "message", "校内社群入口尚未配置。"), requestId(request)); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
