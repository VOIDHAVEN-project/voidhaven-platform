package cn.voidhaven.api.web;

import cn.voidhaven.api.service.*;
import cn.voidhaven.api.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/partner/members")
@PreAuthorize("hasRole('PARTNER')")
public class PartnerMemberController {
    private final PartnerMemberService members;
    private final AccountAdministrationService accounts;
    private final PasswordService passwords;
    private final IdempotencyService idempotency;
    public PartnerMemberController(PartnerMemberService members, AccountAdministrationService accounts, PasswordService passwords, IdempotencyService idempotency) {
        this.members = members; this.accounts = accounts; this.passwords = passwords; this.idempotency = idempotency;
    }
    record Create(@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,100}") String loginId, @NotBlank @Size(max = 1000) String reason) { }
    record Reason(@NotBlank @Size(max = 1000) String reason) { }
    @GetMapping
    ApiResponse<PartnerMemberService.Members> list(Authentication auth, HttpServletRequest request) { return ok(members.list(actor(auth)), request); }
    @PostMapping
    ApiResponse<PartnerMemberService.Member> create(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Create body, Authentication auth, HttpServletRequest request) {
        members.list(actor(auth));
        return ok(idempotency.execute(key, body, PartnerMemberService.Member.class, () -> members.create(body.loginId(), body.reason(), actor(auth), requestId(request))), request);
    }
    @PostMapping("/{id}/activation-links")
    ApiResponse<AccountAdministrationService.IssuedActivationLink> activation(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) {
        members.requireMember(actor(auth), id);
        return ok(idempotency.executeSingleDelivery(key, Map.of("id", id, "reason", body.reason()), () -> accounts.reissueActivation(id, body.reason(), actor(auth), "PARTNER", requestId(request))), request);
    }
    @PostMapping("/{id}/password-reset-links")
    ApiResponse<PasswordService.IssuedReset> reset(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) {
        members.requireMember(actor(auth), id);
        return ok(idempotency.executeSingleDelivery(key, Map.of("id", id, "reason", body.reason()), () -> passwords.issueReset(id, body.reason(), actor(auth), "PARTNER", requestId(request))), request);
    }
    @PostMapping("/{id}/suspend")
    ApiResponse<AccountAdministrationService.AccountStatus> suspend(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) { return transition(id, key, body, "SUSPEND", auth, request); }
    @PostMapping("/{id}/reactivate")
    ApiResponse<AccountAdministrationService.AccountStatus> reactivate(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) { return transition(id, key, body, "REACTIVATE", auth, request); }
    @PostMapping("/{id}/revoke")
    ApiResponse<AccountAdministrationService.AccountStatus> revoke(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) { return transition(id, key, body, "REVOKE", auth, request); }
    private ApiResponse<AccountAdministrationService.AccountStatus> transition(String id, String key, Reason body, String action, Authentication auth, HttpServletRequest request) {
        members.requireMember(actor(auth), id);
        return ok(idempotency.execute(key, Map.of("id", id, "action", action, "reason", body.reason()), AccountAdministrationService.AccountStatus.class,
                () -> switch (action) {
                    case "SUSPEND" -> accounts.suspend(id, body.reason(), actor(auth), "PARTNER", requestId(request));
                    case "REACTIVATE" -> accounts.reactivate(id, body.reason(), actor(auth), "PARTNER", requestId(request));
                    default -> accounts.revoke(id, body.reason(), actor(auth), "PARTNER", requestId(request));
                }), request);
    }
    private String actor(Authentication auth) { return ((AccountPrincipal) auth.getPrincipal()).accountId(); }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
    private <T> ApiResponse<T> ok(T data, HttpServletRequest request) { return new ApiResponse<>(data, requestId(request)); }
}
