package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import cn.voidhaven.api.support.PageResult;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final ContentManagementService content;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    private final PartnerAccountProvisioningService provisioning;
    private final PartnerAdministrationService partnerAdministration;
    private final AuditQueryService auditQuery;
    private final AccountAdministrationService accountAdministration;
    private final PublicInputAdministrationService publicInputAdministration;

    public AdminController(ContentManagementService content, IdempotencyService idempotency, AccountRepository accounts,
                           PartnerAccountProvisioningService provisioning, PartnerAdministrationService partnerAdministration,
                           AuditQueryService auditQuery, AccountAdministrationService accountAdministration,
                           PublicInputAdministrationService publicInputAdministration) {
        this.content = content;
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.provisioning = provisioning;
        this.partnerAdministration = partnerAdministration;
        this.auditQuery = auditQuery;
        this.accountAdministration = accountAdministration;
        this.publicInputAdministration = publicInputAdministration;
    }

    record ReviewDecision(@NotBlank @Pattern(regexp = "APPROVE|CHANGES_REQUESTED|REJECT") String decision,
                          @Size(max = 1000) String reason) { }
    record ActionReason(@NotBlank @Size(max = 1000) String reason) { }
    record ApprovePartnerApplication(@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,100}") String loginId,
                                     @NotBlank @Size(max = 1000) String reason) { }
    record QuotaChange(@Min(0) @Max(5) int quota, @NotBlank @Size(max = 1000) String reason) { }
    record OwnershipTransfer(@NotBlank String contentItemId, @NotBlank String toPartnerProfileId,
                             @NotBlank @Size(max = 1000) String reason) { }
    record OwnershipTransferred(String contentItemId, String toPartnerProfileId) { }
    record PublicInputDecision(@NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
                               @NotBlank @Size(max = 1000) String reason) { }
    record PartnerApplicationDecision(@NotBlank @Pattern(regexp = "REJECT") String decision,
                                      @NotBlank @Size(max = 1000) String reason) { }

    @GetMapping("/review-queue")
    ApiResponse<PageResult<ContentManagementService.Managed>> queue(@RequestParam(required = false) String cursor,
            @RequestParam(required = false) String module, @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        return ok(content.managedPage(null, "PENDING_REVIEW", module, query, cursor, pageSize), request);
    }

    @GetMapping("/content-items")
    ApiResponse<PageResult<ContentManagementService.Managed>> contentItems(@RequestParam(required = false) String cursor,
            @RequestParam(required = false) String status, @RequestParam(required = false) String module,
            @RequestParam(required = false, name = "q") String query, @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ok(content.managedPage(null, status, module, query, cursor, pageSize), request);
    }

    @GetMapping("/content-items/{id}")
    ApiResponse<ContentManagementService.Editable> contentItem(@PathVariable String id, HttpServletRequest request) {
        return ok(content.editable(id), request);
    }

    @GetMapping("/content-items/{id}/publication-readiness")
    ApiResponse<ContentManagementService.PublicationReadiness> publicationReadiness(@PathVariable String id, HttpServletRequest request) {
        return ok(content.publicationReadiness(id), request);
    }

    @GetMapping("/partner-applications")
    ApiResponse<PageResult<PartnerAccountProvisioningService.PendingApplication>> partnerApplications(@RequestParam(required = false) String cursor,
            @RequestParam(required = false, name = "q") String query, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        return ok(provisioning.page(cursor, query, status, pageSize), request);
    }

    @GetMapping("/partner-profiles")
    ApiResponse<PageResult<PartnerAdministrationService.ProfileView>> partnerProfiles(@RequestParam(required = false) String cursor,
            @RequestParam(required = false, name = "q") String query, @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        return ok(partnerAdministration.page(cursor, query, pageSize), request);
    }

    @GetMapping("/submissions")
    ApiResponse<PageResult<PublicInputAdministrationService.Submission>> submissions(@RequestParam(required = false) String cursor,
            @RequestParam(required = false, name = "q") String query, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        return ok(publicInputAdministration.page(cursor, query, status, pageSize), request);
    }

    @GetMapping("/audit-logs")
    ApiResponse<PageResult<AuditQueryService.Entry>> auditLogs(@RequestParam(required = false) String cursor,
            @RequestParam(required = false, name = "q") String query, @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorAccountId, @RequestParam(required = false) String objectType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIT_TIME_RANGE_INVALID", "审计起始时间不能晚于结束时间。");
        }
        return ok(auditQuery.page(cursor, query, action, actorAccountId, objectType, from, to, pageSize), request);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/accounts")
    ApiResponse<PageResult<AccountAdministrationService.AccountStatus>> accountList(@RequestParam(required = false) String cursor,
            @RequestParam(required = false, name = "q") String query, @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        return ok(accountAdministration.page(cursor, query, pageSize), request);
    }

    @PostMapping("/content-items")
    ApiResponse<ContentManagementService.Managed> create(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ContentManagementService.Draft draft, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, draft, ContentManagementService.Managed.class,
                () -> content.create(draft, null, accountId(auth), role(auth), requestId(request))), request);
    }

    @PatchMapping("/content-items/{id}")
    ApiResponse<ContentManagementService.Managed> revise(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("If-Match") String version, @Valid @RequestBody ContentManagementService.Draft draft,
            Authentication auth, HttpServletRequest request) {
        long expected = version(version);
        return ok(idempotency.execute(key, Map.of("id", id, "version", expected, "draft", draft), ContentManagementService.Managed.class,
                () -> content.revise(id, draft, null, expected, accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/content-items/{id}/submit-review")
    ApiResponse<ContentManagementService.Managed> submit(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "action", "SUBMIT"), ContentManagementService.Managed.class,
                () -> content.submitAsAdmin(id, accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/reviews/{id}/decision")
    ApiResponse<ContentManagementService.Managed> decide(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("If-Match") String version, @Valid @RequestBody ReviewDecision decision,
            Authentication auth, HttpServletRequest request) {
        long expected = version(version);
        return ok(idempotency.execute(key, Map.of("id", id, "version", expected, "decision", decision.decision(), "reason", String.valueOf(decision.reason())), ContentManagementService.Managed.class,
                () -> content.decide(id, decision.decision(), decision.reason(), expected, accountId(auth), role(auth), requestId(request))), request);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/content-items/{id}/reopen")
    ApiResponse<ContentManagementService.Managed> reopen(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ActionReason body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "action", "REOPEN", "reason", body.reason()), ContentManagementService.Managed.class,
                () -> content.reopen(id, body.reason(), accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/content-items/{id}/publish")
    ApiResponse<ContentManagementService.Managed> publish(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "action", "PUBLISH"), ContentManagementService.Managed.class,
                () -> content.publish(id, accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/content-items/{id}/hide")
    ApiResponse<ContentManagementService.Managed> hide(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ActionReason body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "action", "HIDE", "reason", body.reason()), ContentManagementService.Managed.class,
                () -> content.hide(id, body.reason().trim(), accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/content-items/{id}/show")
    ApiResponse<ContentManagementService.Managed> show(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "action", "SHOW"), ContentManagementService.Managed.class,
                () -> content.show(id, accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/content-items/{id}/archive")
    ApiResponse<ContentManagementService.Managed> archive(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ActionReason body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "action", "ARCHIVE", "reason", body.reason()), ContentManagementService.Managed.class,
                () -> content.archive(id, body.reason().trim(), accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/content-items/{id}/restore")
    ApiResponse<ContentManagementService.Managed> restore(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ActionReason body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "action", "RESTORE", "reason", body.reason()), ContentManagementService.Managed.class,
                () -> content.restore(id, body.reason().trim(), accountId(auth), role(auth), requestId(request))), request);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/content-items/{id}/delete")
    ApiResponse<ContentManagementService.Managed> delete(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ActionReason body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "action", "DELETE", "reason", body.reason()), ContentManagementService.Managed.class,
                () -> content.delete(id, body.reason().trim(), accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/partner-applications/{id}/approve")
    ApiResponse<PartnerAccountProvisioningService.Provisioned> approvePartnerApplication(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ApprovePartnerApplication body,
            Authentication auth, HttpServletRequest request) {
        return ok(idempotency.executeSingleDelivery(key, Map.of("id", id, "loginId", body.loginId(), "reason", body.reason()),
                () -> provisioning.approveApplication(id, body.loginId(), body.reason(), accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/partner-applications/{id}/decision")
    ApiResponse<PartnerAccountProvisioningService.DecisionResult> decidePartnerApplication(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PartnerApplicationDecision body,
            Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "decision", body.decision(), "reason", body.reason()), PartnerAccountProvisioningService.DecisionResult.class,
                () -> provisioning.decideApplication(id, body.decision(), body.reason(), accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/submissions/{id}/decision")
    ApiResponse<PublicInputAdministrationService.DecisionResult> decideSubmission(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody PublicInputDecision body,
            Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "decision", body.decision(), "reason", body.reason()), PublicInputAdministrationService.DecisionResult.class,
                () -> publicInputAdministration.decideSubmission(id, body.decision(), body.reason(), accountId(auth), role(auth), requestId(request))), request);
    }

    @PatchMapping("/partner-profiles/{id}/content-quota")
    ApiResponse<PartnerAdministrationService.ProfileView> setQuota(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody QuotaChange body,
            Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, Map.of("id", id, "quota", body.quota(), "reason", body.reason()), PartnerAdministrationService.ProfileView.class,
                () -> partnerAdministration.setQuota(id, body.quota(), body.reason().trim(), accountId(auth), role(auth), requestId(request))), request);
    }

    @PostMapping("/content-ownership-transfers")
    ApiResponse<OwnershipTransferred> transferOwnership(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody OwnershipTransfer body, Authentication auth, HttpServletRequest request) {
        return ok(idempotency.execute(key, body, OwnershipTransferred.class, () -> {
            partnerAdministration.transferOwnership(body.contentItemId(), body.toPartnerProfileId(), body.reason().trim(), accountId(auth), role(auth), requestId(request));
            return new OwnershipTransferred(body.contentItemId(), body.toPartnerProfileId());
        }), request);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/accounts/{id}/suspend")
    ApiResponse<AccountAdministrationService.AccountStatus> suspend(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ActionReason body,
            Authentication auth, HttpServletRequest request) {
        return accountAction(id, key, "SUSPEND", body.reason(), request,
                () -> accountAdministration.suspend(id, body.reason(), accountId(auth), role(auth), requestId(request)));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/accounts/{id}/reactivate")
    ApiResponse<AccountAdministrationService.AccountStatus> reactivate(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ActionReason body,
            Authentication auth, HttpServletRequest request) {
        return accountAction(id, key, "REACTIVATE", body.reason(), request,
                () -> accountAdministration.reactivate(id, body.reason(), accountId(auth), role(auth), requestId(request)));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/accounts/{id}/revoke")
    ApiResponse<AccountAdministrationService.AccountStatus> revoke(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ActionReason body,
            Authentication auth, HttpServletRequest request) {
        return accountAction(id, key, "REVOKE", body.reason(), request,
                () -> accountAdministration.revoke(id, body.reason(), accountId(auth), role(auth), requestId(request)));
    }

    private ApiResponse<AccountAdministrationService.AccountStatus> accountAction(String id, String key, String action,
            String reason, HttpServletRequest request, java.util.function.Supplier<AccountAdministrationService.AccountStatus> work) {
        return ok(idempotency.execute(key, Map.of("id", id, "action", action, "reason", reason), AccountAdministrationService.AccountStatus.class, work), request);
    }

    private long version(String value) {
        try { return Long.parseLong(value.replace("\"", "")); }
        catch (NumberFormatException error) { throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_VERSION_REQUIRED", "请携带有效的内容版本后重试。"); }
    }

    private String accountId(Authentication auth) {
        return accounts.findByLoginId(auth.getName()).filter(account -> account.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "当前账号已停用或资格不可用。")).getId();
    }

    private String role(Authentication auth) {
        if (hasRole(auth, "SUPER_ADMIN")) return "SUPER_ADMIN";
        if (hasRole(auth, "ADMIN")) return "ADMIN";
        if (hasRole(auth, "PARTNER")) return "PARTNER";
        return "UNKNOWN";
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(value -> ("ROLE_" + role).equals(value.getAuthority()));
    }

    private <T> ApiResponse<T> ok(T data, HttpServletRequest request) { return new ApiResponse<>(data, requestId(request)); }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
