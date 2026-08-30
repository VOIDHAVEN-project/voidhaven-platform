package cn.voidhaven.api.web;
import cn.voidhaven.api.domain.PartnerProfile;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/partner")
public class PartnerController {
    private final PartnerProfileRepository profiles; private final AccountRepository accounts; private final ContentManagementService content; private final IdempotencyService idempotency;
    public PartnerController(PartnerProfileRepository profiles, AccountRepository accounts, ContentManagementService content, IdempotencyService idempotency) { this.profiles = profiles; this.accounts = accounts; this.content = content; this.idempotency = idempotency; }
    record Dashboard(String displayName, int quota, int publicActiveCount, List<ContentManagementService.Managed> contentItems) { }
    @GetMapping("/dashboard") ApiResponse<Dashboard> dashboard(Authentication auth, HttpServletRequest req) { PartnerProfile profile = profile(auth); List<ContentManagementService.Managed> owned = content.ownedList(profile.getId()); long active = owned.stream().filter(value -> value.workflowStatus().name().equals("PUBLISHED") && value.visibility().name().equals("PUBLIC") && value.recordState().name().equals("ACTIVE")).count(); return ok(new Dashboard(profile.getId(), profile.getContentQuota(), (int) active, owned), req); }
    @GetMapping("/content-items") ApiResponse<List<ContentManagementService.Managed>> list(Authentication auth, HttpServletRequest req) { return ok(content.ownedList(profile(auth).getId()), req); }
    @PostMapping("/content-items") ApiResponse<ContentManagementService.Managed> create(@RequestHeader("Idempotency-Key") String key, @RequestBody ContentManagementService.Draft draft, Authentication auth, HttpServletRequest req) { PartnerProfile profile = profile(auth); String accountId = accountId(auth); return ok(idempotency.execute(key, draft, ContentManagementService.Managed.class, () -> content.create(draft, profile.getId(), accountId, "PARTNER", requestId(req))), req); }
    @PostMapping("/content-items/{id}/submit-review") ApiResponse<ContentManagementService.Managed> submit(@PathVariable String id, @RequestHeader("Idempotency-Key") String key, Authentication auth, HttpServletRequest req) { PartnerProfile profile = profile(auth); String accountId = accountId(auth); return ok(idempotency.execute(key, Map.of("id", id, "action", "SUBMIT"), ContentManagementService.Managed.class, () -> content.submit(id, profile.getId(), accountId, "PARTNER", requestId(req))), req); }
    private PartnerProfile profile(Authentication auth) { return profiles.findByAccountId(accountId(auth)).filter(PartnerProfile::isActive).orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "PARTNER_PROFILE_REQUIRED", "当前账号没有可用的合作方档案。")); }
    private String accountId(Authentication auth) { return accounts.findByLoginId(auth.getName()).orElseThrow().getId(); }
    private <T> ApiResponse<T> ok(T data, HttpServletRequest req) { return new ApiResponse<>(data, requestId(req)); } private String requestId(HttpServletRequest req) { return (String) req.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
