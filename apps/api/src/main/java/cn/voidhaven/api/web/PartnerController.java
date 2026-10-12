package cn.voidhaven.api.web;

import cn.voidhaven.api.domain.PartnerProfile;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import cn.voidhaven.api.service.ContentManagementService;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.ContentActionService;
import cn.voidhaven.api.support.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/v1/partner")
public class PartnerController {
    private cn.voidhaven.api.service.OrganizationAccessService organizations;
    @org.springframework.beans.factory.annotation.Autowired
    void organizations(cn.voidhaven.api.service.OrganizationAccessService organizations) { this.organizations = organizations; }
    private final PartnerProfileRepository profiles;
    private final AccountRepository accounts;
    private final ContentManagementService content;
    private final IdempotencyService idempotency;
    private final ContentActionService contentActions;

    public PartnerController(PartnerProfileRepository profiles, AccountRepository accounts,
                             ContentManagementService content, IdempotencyService idempotency,
                             ContentActionService contentActions) {
        this.profiles = profiles;
        this.accounts = accounts;
        this.content = content;
        this.idempotency = idempotency;
        this.contentActions = contentActions;
    }

    record Dashboard(String displayName, int quota, int publicActiveCount,
                     PageResult<ContentManagementService.Managed> contentItems, cn.voidhaven.api.service.OrganizationAccessService.Identity organization) { }
    record ActionSetRequest(List<ContentActionService.ActionInput> actions) { }

    @GetMapping("/dashboard")
    ApiResponse<Dashboard> dashboard(@RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String module,
                                     @RequestParam(required = false, name = "q") String query,
                                     @RequestParam(defaultValue = "20") int pageSize,
                                     Authentication auth, HttpServletRequest request) {
        PartnerProfile profile = profile(auth);
        PageResult<ContentManagementService.Managed> page = content.managedPage(
                profile.getId(), status, module, query, cursor, pageSize);
        return ok(new Dashboard(profile.getDisplayName(), profile.getContentQuota(),
                Math.toIntExact(content.publicActiveCount(profile.getId())), page, organizations.identity(accountId(auth))), request);
    }

    @GetMapping("/content-items")
    ApiResponse<PageResult<ContentManagementService.Managed>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String module,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication auth, HttpServletRequest request) {
        PartnerProfile profile = profile(auth);
        return ok(content.managedPage(profile.getId(), status, module, query, cursor, pageSize), request);
    }

    @GetMapping("/content-items/{id}")
    ApiResponse<ContentManagementService.Editable> item(@PathVariable String id, Authentication auth,
                                                        HttpServletRequest request) {
        return ok(content.ownedEditable(id, profile(auth).getId()), request);
    }

    @PostMapping("/content-items")
    ApiResponse<ContentManagementService.Managed> create(@RequestHeader("Idempotency-Key") String key,
                                                         @Valid @RequestBody ContentManagementService.Draft draft,
                                                         Authentication auth, HttpServletRequest request) {
        PartnerProfile profile = profile(auth);
        String accountId = accountId(auth);
        return ok(idempotency.execute(key, draft, ContentManagementService.Managed.class,
                () -> content.create(draft, profile.getId(), accountId, "PARTNER", requestId(request))), request);
    }

    @PatchMapping("/content-items/{id}")
    ApiResponse<ContentManagementService.Managed> revise(@PathVariable String id,
                                                         @RequestHeader("Idempotency-Key") String key,
                                                         @RequestHeader("If-Match") String version,
                                                         @Valid @RequestBody ContentManagementService.Draft draft,
                                                         Authentication auth, HttpServletRequest request) {
        PartnerProfile profile = profile(auth);
        String accountId = accountId(auth);
        long expected = version(version);
        return ok(idempotency.execute(key, Map.of("id", id, "version", expected, "draft", draft),
                ContentManagementService.Managed.class,
                () -> content.revise(id, draft, profile.getId(), expected, accountId, "PARTNER", requestId(request))), request);
    }

    @PostMapping("/content-items/{id}/submit-review")
    ApiResponse<ContentManagementService.Managed> submit(@PathVariable String id,
                                                         @RequestHeader("Idempotency-Key") String key,
                                                         Authentication auth, HttpServletRequest request) {
        PartnerProfile profile = profile(auth);
        String accountId = accountId(auth);
        return ok(idempotency.execute(key, Map.of("id", id, "action", "SUBMIT"),
                ContentManagementService.Managed.class,
                () -> content.submit(id, profile.getId(), accountId, "PARTNER", requestId(request))), request);
    }

    @org.springframework.web.bind.annotation.PutMapping("/content-items/{id}/actions")
    ApiResponse<ContentActionService.ActionSet> replaceActions(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader("If-Match") String version,
            @Valid @RequestBody ActionSetRequest body, Authentication auth, HttpServletRequest request) {
        PartnerProfile profile = profile(auth);
        long expected = version(version);
        return ok(idempotency.executeSingleDelivery(key, Map.of("id", id, "version", expected, "actions", body.actions()),
                () -> contentActions.replace(id, profile.getId(), expected, body.actions(), accountId(auth), "PARTNER", requestId(request))), request);
    }

    @GetMapping("/content-items/{id}/actions")
    ApiResponse<List<ContentActionService.ManagedAction>> actions(@PathVariable String id, Authentication auth, HttpServletRequest request) {
        return ok(contentActions.managedActions(id, profile(auth).getId()), request);
    }

    private long version(String value) {
        try {
            return Long.parseLong(value.replace("\"", ""));
        } catch (NumberFormatException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_VERSION_REQUIRED", "请携带有效的内容版本后重试。");
        }
    }

    private PartnerProfile profile(Authentication auth) {
        if (organizations != null) return organizations.require(accountId(auth));
        return profiles.findByAccountId(accountId(auth)).filter(PartnerProfile::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PARTNER_PROFILE_REQUIRED",
                        "当前账号没有可用的合作方档案。"));
    }

    private String accountId(Authentication auth) {
        return accounts.findByLoginId(auth.getName()).filter(account -> account.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE",
                        "当前账号已停用或资格不可用。"))
                .getId();
    }

    private <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return new ApiResponse<>(data, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
