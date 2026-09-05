package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.PartnerProfile;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import cn.voidhaven.api.persistence.RoleRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.support.PageResult;
import cn.voidhaven.api.web.ApiException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerAccountProvisioningService {
    public record Provisioned(String partnerProfileId, String loginId, String activationToken) { }
    public record PendingApplication(String id, String displayName, String introduction, String portfolioUrl, String state) { }
    public record DecisionResult(String id, String state) { }
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final PartnerProfileRepository profiles;
    private final RoleRepository roles;
    private final ActivationService activations;
    private final AuditService audit;

    public PartnerAccountProvisioningService(JdbcTemplate jdbc, AccountRepository accounts, PartnerProfileRepository profiles, RoleRepository roles, ActivationService activations, AuditService audit) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.profiles = profiles;
        this.roles = roles;
        this.activations = activations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public java.util.List<PendingApplication> pendingApplications() {
        return jdbc.query("select id, display_name, introduction, portfolio_url, state from partner_applications where state = 'PENDING_REVIEW' order by created_at asc", (result, row) -> new PendingApplication(result.getString("id"), result.getString("display_name"), result.getString("introduction"), result.getString("portfolio_url"), result.getString("state")));
    }

    @Transactional(readOnly = true)
    public PageResult<PendingApplication> page(String cursor, String query, String status, int requestedSize) {
        int pageSize = Math.max(1, Math.min(requestedSize, 50));
        String normalized = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        String normalizedStatus = normalizeStatus(status);
        java.util.List<PendingApplication> values = jdbc.query("select id, display_name, introduction, portfolio_url, state from partner_applications where (? is null or state = ?) and (? is null or lower(display_name) like ? or lower(introduction) like ?) and (? is null or id < ?) order by id desc limit ?",
                (row, index) -> new PendingApplication(row.getString("id"), row.getString("display_name"), row.getString("introduction"), row.getString("portfolio_url"), row.getString("state")),
                normalizedStatus, normalizedStatus, normalized, normalized, normalized, cursor, cursor, pageSize + 1);
        return PageResult.from(values, pageSize, PendingApplication::id);
    }

    @Transactional
    public Provisioned approveApplication(String applicationId, String loginId, String reason, String actorId, String actorRole, String requestId) {
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_REASON_REQUIRED", "请填写批准合作方申请的原因。");
        String displayName;
        try {
            displayName = jdbc.queryForObject("select display_name from partner_applications where id = ? and state = 'PENDING_REVIEW'", String.class, applicationId);
        } catch (EmptyResultDataAccessException missing) {
            throw new ApiException(HttpStatus.CONFLICT, "PARTNER_APPLICATION_NOT_ACTIONABLE", "该合作方申请不存在或已被处理。");
        }
        if (accounts.findByLoginId(loginId).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_ALREADY_EXISTS", "该受控登录名已被使用。");
        Role partnerRole = roles.findByCode("PARTNER").orElseGet(() -> roles.save(new Role("PARTNER")));
        Account account = new Account(Ids.ulid(), loginId, "!ACTIVATION_REQUIRED!", "PENDING");
        account.addRole(partnerRole);
        accounts.saveAndFlush(account);
        PartnerProfile profile = profiles.saveAndFlush(new PartnerProfile(Ids.ulid(), account.getId(), displayName, 3));
        int updated = jdbc.update("update partner_applications set state = 'APPROVED', reviewed_by_account_id = ?, review_reason = ?, reviewed_at = current_timestamp(6) where id = ? and state = 'PENDING_REVIEW'", actorId, normalizedReason, applicationId);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "PARTNER_APPLICATION_NOT_ACTIONABLE", "该合作方申请已被其他操作处理。 ");
        String token = activations.issue(account.getId());
        audit.record(actorId, actorRole, "PARTNER_ACCOUNT_CREATED", "PARTNER_PROFILE", profile.getId(), "{}", "{\"applicationId\":\"" + applicationId + "\",\"qualificationStatus\":\"PENDING\"}", normalizedReason, requestId);
        audit.record(actorId, actorRole, "ACTIVATION_ISSUED", "ACCOUNT", account.getId(), "{}", "{\"qualificationStatus\":\"PENDING\"}", null, requestId);
        return new Provisioned(profile.getId(), account.getLoginId(), token);
    }

    @Transactional
    public DecisionResult decideApplication(String applicationId, String decision, String reason, String actorId, String actorRole, String requestId) {
        if (!"REJECT".equals(decision)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARTNER_APPLICATION_DECISION", "合作方申请只能批准或拒绝；匿名申请不支持退回修改。 ");
        }
        String state = "REJECTED";
        int updated = jdbc.update("update partner_applications set state = ?, reviewed_by_account_id = ?, review_reason = ?, reviewed_at = current_timestamp(6) where id = ? and state = 'PENDING_REVIEW'", state, actorId, reason, applicationId);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "PARTNER_APPLICATION_NOT_ACTIONABLE", "该合作方申请不存在或已被处理。 ");
        audit.record(actorId, actorRole, "PARTNER_APPLICATION_" + decision, "PARTNER_APPLICATION", applicationId,
                "{\"state\":\"PENDING_REVIEW\"}", "{\"state\":\"" + state + "\"}", reason, requestId);
        return new DecisionResult(applicationId, state);
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? "PENDING_REVIEW" : status.trim();
        if ("ALL".equals(normalized)) return null;
        if (!java.util.Set.of("PENDING_REVIEW", "APPROVED", "REJECTED").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PARTNER_APPLICATION_STATUS_INVALID", "合作方申请状态筛选无效。");
        }
        return normalized;
    }
}
