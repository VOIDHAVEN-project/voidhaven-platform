package cn.voidhaven.api.web;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/admin")
public class AdminController {
    private final ContentManagementService content; private final IdempotencyService idempotency; private final AccountRepository accounts;
    public AdminController(ContentManagementService content, IdempotencyService idempotency, AccountRepository accounts) { this.content = content; this.idempotency = idempotency; this.accounts = accounts; }
    record ReviewDecision(String decision, @Size(max = 1000) String reason) { }
    record ActionReason(@Size(max = 1000) String reason) { }
    @GetMapping("/review-queue") ApiResponse<List<ContentManagementService.Managed>> queue(HttpServletRequest req) { return ok(content.pending(), req); }
    @PostMapping("/content-items") ApiResponse<ContentManagementService.Managed> create(@RequestHeader("Idempotency-Key") String key, @RequestBody ContentManagementService.Draft draft, Authentication auth, HttpServletRequest req) { return ok(idempotency.execute(key, draft, ContentManagementService.Managed.class, () -> content.create(draft, null, accountId(auth), role(auth), requestId(req))), req); }
    @PostMapping("/reviews/{id}/decision") ApiResponse<ContentManagementService.Managed> decide(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody ReviewDecision decision, Authentication auth, HttpServletRequest req) { return ok(idempotency.execute(key, Map.of("id", id, "decision", decision.decision(), "reason", String.valueOf(decision.reason())), ContentManagementService.Managed.class, () -> content.decide(id, decision.decision(), decision.reason(), accountId(auth), role(auth), requestId(req))), req); }
    @PostMapping("/content-items/{id}/publish") ApiResponse<ContentManagementService.Managed> publish(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, Authentication auth, HttpServletRequest req) { return ok(idempotency.execute(key, Map.of("id", id, "action", "PUBLISH"), ContentManagementService.Managed.class, () -> content.publish(id, accountId(auth), role(auth), requestId(req))), req); }
    @PostMapping("/content-items/{id}/hide") ApiResponse<ContentManagementService.Managed> hide(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody ActionReason action, Authentication auth, HttpServletRequest req) { return ok(idempotency.execute(key, Map.of("id", id, "action", "HIDE", "reason", String.valueOf(action.reason())), ContentManagementService.Managed.class, () -> content.hide(id, action.reason(), accountId(auth), role(auth), requestId(req))), req); }
    @PostMapping("/content-items/{id}/archive") ApiResponse<ContentManagementService.Managed> archive(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody ActionReason action, Authentication auth, HttpServletRequest req) { return ok(idempotency.execute(key, Map.of("id", id, "action", "ARCHIVE", "reason", String.valueOf(action.reason())), ContentManagementService.Managed.class, () -> content.archive(id, action.reason(), accountId(auth), role(auth), requestId(req))), req); }
    private String accountId(Authentication auth) { return accounts.findByLoginId(auth.getName()).orElseThrow().getId(); } private String role(Authentication auth) { return auth.getAuthorities().stream().findFirst().map(value -> value.getAuthority().replaceFirst("^ROLE_", "")).orElse("ADMIN"); } private <T> ApiResponse<T> ok(T data, HttpServletRequest req) { return new ApiResponse<>(data, requestId(req)); } private String requestId(HttpServletRequest req) { return (String) req.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
