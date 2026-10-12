package cn.voidhaven.api.web;

import cn.voidhaven.api.service.AdminAuthorizationService;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.*;
import java.util.*;

/** Explicit route catalogue. Unknown administrative routes never inherit ADMIN privileges. */
@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {
    private final AdminAuthorizationService authorization;
    public AdminAuthorizationInterceptor(AdminAuthorizationService authorization) { this.authorization = authorization; }
    private static final Map<String, String> RULES = rules();
    private static Map<String, String> rules() {
        Map<String, String> rules = new HashMap<>();
        add(rules, "CONTENT_READ", "GET /content-items", "GET /content-items/{id}", "GET /content-items/{id}/publication-readiness", "GET /content-items/{id}/actions", "GET /content-items/{id}/verifications");
        add(rules, "CONTENT_EDIT", "POST /content-items", "PATCH /content-items/{id}", "POST /content-items/{id}/submit-review", "PUT /content-items/{id}/actions");
        add(rules, "CONTENT_REVIEW", "GET /review-queue", "POST /reviews/{id}/decision");
        add(rules, "CONTENT_PUBLISH", "POST /content-items/{id}/publish", "POST /content-items/{id}/hide", "POST /content-items/{id}/show", "POST /content-items/{id}/archive", "POST /content-items/{id}/restore", "POST /content-items/{id}/operational-status");
        add(rules, "CONTENT_VERIFY", "POST /content-items/{id}/verifications");
        add(rules, "PARTNER_VERIFY", "GET /partner-applications", "POST /partner-applications/{id}/approve", "POST /partner-applications/{id}/decision", "POST /partner-applications/{id}/transition", "POST /partner-applications/{id}/contact");
        add(rules, "PUBLIC_INPUT_OPERATE", "GET /submissions", "POST /submissions/{id}/decision", "POST /submissions/{id}/transition", "POST /submissions/{id}/contact");
        add(rules, "ACCOUNT_OPERATE", "GET /accounts", "POST /accounts/{id}/activation-links", "POST /accounts/{id}/suspend", "POST /accounts/{id}/reactivate", "POST /accounts/{id}/revoke", "POST /partner-profiles/{id}/suspend", "POST /partner-profiles/{id}/reactivate");
        add(rules, "QUOTA_CONFIG", "PATCH /partner-profiles/{id}/content-quota", "PATCH /partner-profiles/{id}/member-quota");
        add(rules, "OWNERSHIP_TRANSFER", "POST /content-ownership-transfers");
        add(rules, "AUDIT_READ", "GET /audit-logs");
        add(rules, "HOMEPAGE_CONFIG", "GET /homepage-placements", "GET /homepage-placements/candidates", "POST /homepage-placements/preview", "PUT /homepage-placements");
        add(rules, "CATALOG_CONFIG", "GET /catalog-order/{kind}", "GET /catalog-order/{kind}/candidates", "PUT /catalog-order/{kind}");
        add(rules, "RESOURCE_CONFIG", "GET /resource-config", "GET /resource-config/versions", "PUT /resource-config");
        add(rules, "SUPER_ONLY", "POST /accounts", "POST /accounts/authorization-preview", "POST /accounts/{id}/password-reset-links", "POST /content-items/{id}/delete", "POST /content-items/{id}/reopen", "GET /permission-catalog", "GET /accounts/{id}/authorization", "POST /accounts/{id}/authorization/preview", "PUT /accounts/{id}/authorization", "POST /accounts/{id}/authorization/revoke", "GET /accounts/{id}/authorization/history");
        add(rules, "ORGANIZATION_READ", "GET /partner-profiles"); add(rules, "TASKS", "GET /tasks");
        return Map.copyOf(rules);
    }
    private static void add(Map<String, String> rules, String permission, String... routes) { for (String route : routes) rules.put(route, permission); }
    public static String rule(String method, String path) {
        if (path.startsWith("/api/v1/admin/")) path = path.substring("/api/v1/admin".length());
        if (path.startsWith("/community/") || path.equals("/community") || path.startsWith("/participation/project-join") || path.startsWith("/manual-deliveries")) return "SUPER_ONLY";
        return RULES.get(method + " " + path);
    }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = Objects.toString(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE), request.getRequestURI());
        if (!path.startsWith("/api/v1/admin/")) return true;
        String permission = rule(request.getMethod(), path);
        if (permission == null) throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "AUTHORIZATION_RULE_REQUIRED", "此后台操作尚未配置授权规则。" );
        if (authorization.isSuper()) return true;
        if (permission == null || permission.equals("SUPER_ONLY")) authorization.requireSuper();
        if (permission.equals("TASKS")) return true;
        if (permission.equals("ORGANIZATION_READ")) {
            if (!authorization.has("ACCOUNT_OPERATE") && !authorization.has("QUOTA_CONFIG") && !authorization.has("OWNERSHIP_TRANSFER")) authorization.requireSuper();
            return true;
        }
        @SuppressWarnings("unchecked") Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String id = variables == null ? null : variables.get("id");
        if (permission.startsWith("CONTENT_") && id != null) authorization.requireContent(permission, id);
        else if (permission.equals("CONTENT_READ")) {
            if (authorization.effective().stream().noneMatch(grant -> grant.permission().startsWith("CONTENT_"))) authorization.requireSuper();
        } else if (permission.equals("ACCOUNT_OPERATE") && id != null) {
            if (path.contains("/partner-profiles/")) authorization.requireOrganization(permission, id);
            else authorization.requirePartnerAccount(id);
        }
        else if (permission.equals("QUOTA_CONFIG") && id != null) authorization.requireOrganization(permission, id);
        else authorization.require(permission);
        if (path.endsWith("/contact")) authorization.requireGlobal("PRIVATE_DATA_READ");
        request.setAttribute("admin.contentPermission", permission); return true;
    }
}
