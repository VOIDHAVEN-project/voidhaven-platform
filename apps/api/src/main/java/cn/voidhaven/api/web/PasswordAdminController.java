package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PasswordAdminController {
    record Reason(@NotBlank @Size(max = 1000) String reason) { }
    private final PasswordService passwords;
    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    public PasswordAdminController(PasswordService passwords, IdempotencyService idempotency, AccountRepository accounts) { this.passwords = passwords; this.idempotency = idempotency; this.accounts = accounts; }
    @PostMapping("/{id}/password-reset-links")
    ApiResponse<PasswordService.IssuedReset> issue(@PathVariable String id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody Reason body, Authentication auth, HttpServletRequest request) {
        return new ApiResponse<>(idempotency.executeSingleDelivery(key, Map.of("id", id, "reason", body.reason()),
                () -> passwords.issueReset(id, body.reason(), actorId(auth), "SUPER_ADMIN", requestId(request))), requestId(request));
    }
    private String actorId(Authentication auth) { return accounts.findByLoginId(auth.getName()).orElseThrow().getId(); }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
