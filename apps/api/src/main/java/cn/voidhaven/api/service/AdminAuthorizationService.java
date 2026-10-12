package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.security.AccountPrincipal;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class AdminAuthorizationService {
    public record Permission(String code, String label, List<String> scopeTypes) { }
    public record Grant(String permission, String scopeType, List<String> scopeIds) { }
    public record Input(List<Grant> grants, Boolean longTerm, Instant expiresAt, String reason) { }
    public record Authorization(List<Grant> grants, boolean longTerm, Instant expiresAt, long version) { }
    public record Preview(Authorization before, Authorization proposed, List<String> added, List<String> removed, List<String> impacts) { }
    private static final Set<String> CONTENT = Set.of("CONTENT_READ", "CONTENT_EDIT", "CONTENT_REVIEW", "CONTENT_PUBLISH", "CONTENT_VERIFY");
    private static Permission permission(String code, String label, String... scopes) { return new Permission(code, label, List.of(scopes)); }
    public static final List<Permission> CATALOG = List.of(
        permission("CONTENT_READ", "内容查看", "GLOBAL", "MODULES", "ORGANIZATIONS"),
        permission("CONTENT_EDIT", "内容编辑", "GLOBAL", "MODULES", "ORGANIZATIONS"),
        permission("CONTENT_REVIEW", "内容审核", "GLOBAL", "MODULES", "ORGANIZATIONS"),
        permission("CONTENT_PUBLISH", "内容发布与生命周期", "GLOBAL", "MODULES", "ORGANIZATIONS"),
        permission("CONTENT_VERIFY", "来源核验", "GLOBAL", "MODULES", "ORGANIZATIONS"),
        permission("PARTNER_VERIFY", "合作方核验", "GLOBAL"),
        permission("ACCOUNT_OPERATE", "合作方账号运营", "GLOBAL", "ORGANIZATIONS"),
        permission("PRIVATE_DATA_READ", "私密资料查看", "GLOBAL", "ORGANIZATIONS"),
        permission("OWNERSHIP_TRANSFER", "归属转交", "GLOBAL", "ORGANIZATIONS"),
        permission("QUOTA_CONFIG", "配额配置", "GLOBAL", "ORGANIZATIONS"),
        permission("AUDIT_READ", "平台审计查看", "GLOBAL"),
        permission("PUBLIC_INPUT_OPERATE", "公开投稿与反馈处理", "GLOBAL"),
        permission("HOMEPAGE_CONFIG", "首页编排", "GLOBAL"),
        permission("CATALOG_CONFIG", "目录排序", "GLOBAL"),
        permission("RESOURCE_CONFIG", "资源展示设置", "GLOBAL")
    );
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final ObjectMapper json;
    private final AuditService audit;
    private final SecurityRevocationService revocations;
    private final ContentModuleRegistry modules;
    public AdminAuthorizationService(JdbcTemplate jdbc, AccountRepository accounts, ObjectMapper json, AuditService audit,
                                     SecurityRevocationService revocations, @org.springframework.context.annotation.Lazy ContentModuleRegistry modules) {
        this.jdbc = jdbc; this.accounts = accounts; this.json = json; this.audit = audit; this.revocations = revocations; this.modules = modules;
    }
    public Account actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AccountPrincipal principal)) throw denied();
        return accounts.findById(principal.accountId()).filter(principal::matches).orElseThrow(this::denied);
    }
    public boolean isSuper() { return actor().hasRole("SUPER_ADMIN"); }
    public boolean isAdmin() { var actor = actor(); return actor.hasRole("ADMIN") || actor.hasRole("SUPER_ADMIN"); }
    public void requireSuper() { if (!isSuper()) throw denied(); }
    public Authorization current(String accountId) {
        return jdbc.query("select grants, long_term, expires_at, version from admin_authorizations where account_id = ?", row -> {
            if (!row.next()) return new Authorization(List.of(), false, null, 0);
            var expiry = row.getTimestamp("expires_at");
            return new Authorization(Arrays.asList(json.readValue(row.getString("grants"), Grant[].class)), row.getBoolean("long_term"),
                    expiry == null ? null : expiry.toInstant(), row.getLong("version"));
        }, accountId);
    }
    public List<Grant> effective() {
        Account actor = actor(); if (!actor.hasRole("ADMIN")) return List.of();
        Authorization value = current(actor.getId());
        if (!value.longTerm() && (value.expiresAt() == null || !Instant.now().isBefore(value.expiresAt()))) return List.of();
        return value.grants();
    }
    public boolean has(String permission) {
        return isSuper() || effective().stream().anyMatch(grant -> grant.permission().equals(permission));
    }
    public boolean allows(String permission, String module, String organization) {
        if (isSuper()) return true;
        return effective().stream().anyMatch(grant -> (grant.permission().equals(permission)
                || (permission.equals("CONTENT_READ") && CONTENT.contains(grant.permission()))) && matches(grant, module, organization));
    }
    private boolean matches(Grant grant, String module, String organization) {
        return switch (grant.scopeType()) {
            case "GLOBAL" -> true;
            case "MODULES" -> module != null && grant.scopeIds().contains(module);
            case "ORGANIZATIONS" -> organization != null && grant.scopeIds().contains(organization);
            default -> false;
        };
    }
    public void require(String permission) { if (!has(permission)) throw denied(); }
    public void requireGlobal(String permission) { if (!allows(permission, null, null)) throw denied(); }
    public void requireOrganization(String permission, String id) { if (!allows(permission, null, id)) throw denied(); }
    public void requireContent(String permission, String id) {
        Map<String, Object> item = jdbc.query("select module_key, owner_partner_profile_id from content_items where id = ?",
                row -> row.next() ? Map.of("module", row.getString(1), "organization", row.getString(2) == null ? "" : row.getString(2)) : null, id);
        if (item == null) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或不可访问。");
        if (!allows(permission, item.get("module").toString(), item.get("organization").toString())) throw denied();
    }
    public void requireCreate(String module) { if (!allows("CONTENT_EDIT", module, null)) throw denied(); }
    public void requireItem(String permission, ContentItem item) {
        if (!allows(permission, item.getModuleKey(), item.getOwnerPartnerProfileId())) throw denied();
    }
    public void recheckWrite() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)) return;
        String permission = Objects.toString(servlet.getRequest().getAttribute("admin.contentPermission"), "");
        if (!permission.isEmpty() && !Set.of("TASKS", "ORGANIZATION_READ", "SUPER_ONLY").contains(permission)) require(permission);
    }
    public boolean canIssueActivation(String issuerId, String targetId) {
        Account issuer = accounts.findById(issuerId).orElse(null); if (issuer == null || !issuer.isActive()) return false;
        if (issuer.hasRole("SUPER_ADMIN")) return true;
        if (!issuer.hasRole("ADMIN")) return false;
        var target = accounts.findById(targetId).orElse(null);
        if (target == null || !target.hasRole("PARTNER") || target.hasRole("ADMIN") || target.hasRole("SUPER_ADMIN")) return false;
        String organization = jdbc.query("select id from partner_profiles where account_id = ?", row -> row.next() ? row.getString(1) : null, targetId);
        if (organization == null) organization = memberOrganization(targetId);
        Authorization value = current(issuerId);
        if (!value.longTerm() && (value.expiresAt() == null || !Instant.now().isBefore(value.expiresAt()))) return false;
        final String profile = organization;
        return value.grants().stream().anyMatch(grant -> grant.permission().equals("ACCOUNT_OPERATE") && matches(grant, null, profile));
    }
    public void requirePartnerAccount(String id) {
        var account = accounts.findById(id).orElseThrow(this::denied);
        if (!account.hasRole("PARTNER") || account.hasRole("ADMIN") || account.hasRole("SUPER_ADMIN")) throw denied();
        String profile = jdbc.query("select id from partner_profiles where account_id = ?", row -> row.next() ? row.getString(1) : null, id);
        if (profile == null) profile = memberOrganization(id);
        requireOrganization("ACCOUNT_OPERATE", profile);
    }
    private String memberOrganization(String id) {
        return jdbc.query("select partner_profile_id from partner_members where account_id = ?", row -> row.next() ? row.getString(1) : null, id);
    }
    public String contentPredicate(String permission, String alias, List<Object> arguments) {
        if (isSuper()) return "1=1";
        List<Grant> eligible = effective().stream().filter(g -> g.permission().equals(permission) || (permission.equals("CONTENT_READ") && CONTENT.contains(g.permission()))).toList();
        if (eligible.stream().anyMatch(g -> g.scopeType().equals("GLOBAL"))) return "1=1";
        List<String> clauses = new ArrayList<>();
        for (Grant grant : eligible) {
            if (!grant.permission().equals(permission) && !(permission.equals("CONTENT_READ") && CONTENT.contains(grant.permission()))) continue;
            if (grant.scopeType().equals("GLOBAL")) return "1=1";
            if (grant.scopeIds().isEmpty()) continue;
            String column = grant.scopeType().equals("MODULES") ? ".module_key" : ".owner_partner_profile_id";
            clauses.add(alias + column + " in (" + String.join(",", Collections.nCopies(grant.scopeIds().size(), "?")) + ")");
            arguments.addAll(grant.scopeIds());
        }
        return clauses.isEmpty() ? "1=0" : "(" + String.join(" or ", clauses) + ")";
    }
    public boolean canSeeOrganization(String id) {
        return isSuper() || java.util.stream.Stream.of("ACCOUNT_OPERATE", "QUOTA_CONFIG", "OWNERSHIP_TRANSFER")
                .anyMatch(permission -> allows(permission, null, id));
    }
    public List<JsonNode> history(String id) {
        requireSuper(); target(id);
        return jdbc.query("select id, created_at, actor_account_id, before_summary, after_summary, reason from audit_logs where object_type = 'ACCOUNT' and object_id = ? and action in ('ADMIN_AUTHORIZATION_CHANGED','ADMIN_AUTHORIZATION_REVOKED') order by created_at desc, id desc limit 100", (row, index) -> {
            var result = json.createObjectNode(); result.put("id", row.getString(1)); result.put("createdAt", row.getTimestamp(2).toInstant().toString());
            result.put("actorAccountId", row.getString(3)); result.set("before", json.readTree(row.getString(4)));
            result.set("after", json.readTree(row.getString(5))); result.put("reason", row.getString(6)); return result;
        }, id);
    }
    public Preview preview(String id, Input input) {
        requireSuper(); target(id); return buildPreview(current(id), input);
    }
    public Preview previewNew(Input input) { requireSuper(); return buildPreview(new Authorization(List.of(), false, null, 0), input); }
    private Preview buildPreview(Authorization before, Input input) {
        Authorization next = validated(input, before.version() + 1);
        var old = before.grants().stream().map(Grant::permission).toList(); var fresh = next.grants().stream().map(Grant::permission).toList();
        List<String> impacts = new ArrayList<>(List.of("保存将使目标管理员旧会话与未使用凭据失效。", "组织范围编辑仅维护既有组织内容，不能创建平台草稿。"));
        if (fresh.contains("PRIVATE_DATA_READ")) impacts.add("允许在业务访问范围内读取私密资料。");
        if (fresh.containsAll(List.of("CONTENT_EDIT", "CONTENT_REVIEW", "CONTENT_PUBLISH"))) impacts.add("同一人可完成编辑、审核和发布，仍须经过审核通过状态。");
        return new Preview(before, next, fresh.stream().filter(p -> !old.contains(p)).toList(), old.stream().filter(p -> !fresh.contains(p)).toList(), impacts);
    }
    @Transactional
    public Authorization replace(String id, long expected, Input input, String requestId) {
        requireSuper(); Account account = accounts.findByIdForUpdate(id).orElseThrow(this::denied); target(id);
        Authorization before = current(id); if (expected != before.version()) throw new ApiException(HttpStatus.CONFLICT, "AUTHORIZATION_VERSION_CONFLICT", "授权已变化，请刷新后重新预览。");
        Authorization next = validated(input, before.version() + 1);
        jdbc.update("insert into admin_authorizations (account_id, grants, long_term, expires_at, version) values (?, cast(? as json), ?, ?, ?) on duplicate key update grants = values(grants), long_term = values(long_term), expires_at = values(expires_at), version = values(version)",
                id, json.writeValueAsString(next.grants()), next.longTerm(), next.expiresAt() == null ? null : Timestamp.from(next.expiresAt()), next.version());
        revocations.revoke(account);
        audit.record(actor().getId(), "SUPER_ADMIN", next.grants().isEmpty() ? "ADMIN_AUTHORIZATION_REVOKED" : "ADMIN_AUTHORIZATION_CHANGED", "ACCOUNT", id,
                json.writeValueAsString(before), json.writeValueAsString(next), input.reason(), requestId);
        return next;
    }
    public void target(String id) {
        accounts.findById(id).filter(account -> account.hasRole("ADMIN") && !account.hasRole("SUPER_ADMIN") && !account.hasRole("PARTNER"))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_AUTHORIZATION_TARGET_INVALID", "只能配置普通管理员的授权。"));
    }
    private Authorization validated(Input input, long version) {
        if (input == null || input.grants() == null || input.grants().size() > CATALOG.size() || input.longTerm() == null
                || input.reason() == null || input.reason().isBlank() || input.reason().length() > 1000
                || (input.longTerm() ? input.expiresAt() != null : input.expiresAt() == null || !input.expiresAt().isAfter(Instant.now())))
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUTHORIZATION_INVALID", "请明确选择授权期限并填写有效的授权和原因。");
        Set<String> seen = new HashSet<>(); List<Grant> grants = new ArrayList<>();
        for (Grant grant : input.grants()) {
            if (grant == null) throw denied();
            Permission definition = CATALOG.stream().filter(p -> p.code().equals(grant.permission())).findFirst().orElseThrow(this::denied);
            if (!seen.add(grant.permission()) || !definition.scopeTypes().contains(grant.scopeType()) || grant.scopeIds() == null || grant.scopeIds().size() > 1000 || grant.scopeIds().stream().anyMatch(id -> id == null || id.isBlank())
                    || (grant.scopeType().equals("GLOBAL") ? !grant.scopeIds().isEmpty() : grant.scopeIds().isEmpty())) throw denied();
            List<String> ids = grant.scopeIds().stream().distinct().sorted().toList();
            for (String value : ids) {
                if (grant.scopeType().equals("MODULES")) modules.requireKnown(value);
                if (grant.scopeType().equals("ORGANIZATIONS") && !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from partner_profiles where id = ?)", Boolean.class, value))) throw denied();
            }
            grants.add(new Grant(grant.permission(), grant.scopeType(), ids));
        }
        grants.sort(Comparator.comparing(Grant::permission)); return new Authorization(grants, input.longTerm(), input.expiresAt(), version);
    }
    private ApiException denied() { return new ApiException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "当前账号没有此操作或范围的权限。"); }
}
