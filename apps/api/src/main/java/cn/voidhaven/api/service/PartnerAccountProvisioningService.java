package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.PartnerProfile;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import cn.voidhaven.api.persistence.RoleRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.support.LoginIds;
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
    public record PendingApplication(String id, String displayName, String introduction, String portfolioUrl, String state,
                                     String publicResult, java.time.Instant dueAt, long version, boolean contactAvailable) { }
    public record DecisionResult(String id, String state) { }
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final PartnerProfileRepository profiles;
    private final RoleRepository roles;
    private final ActivationService activations;
    private final AuditService audit;
    private AccountLockService accountLocks;

    public PartnerAccountProvisioningService(JdbcTemplate jdbc, AccountRepository accounts, PartnerProfileRepository profiles, RoleRepository roles, ActivationService activations, AuditService audit) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.profiles = profiles;
        this.roles = roles;
        this.activations = activations;
        this.audit = audit;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAccountLocks(AccountLockService accountLocks) { this.accountLocks = accountLocks; }

    @Transactional(readOnly = true)
    public java.util.List<PendingApplication> pendingApplications() {
        return jdbc.query("select a.id, a.display_name, a.introduction, a.portfolio_url, a.state, a.public_result, a.due_at, a.version, exists(select 1 from private_contact_records p where p.subject_type = 'PARTNER_APPLICATION' and p.subject_id = a.id and p.delete_after > current_timestamp(6)) contact_available from partner_applications a where a.state in ('RECEIVED','PENDING_REVIEW','IN_REVIEW') order by a.created_at asc", (result, row) -> pending(result));
    }

    @Transactional(readOnly = true)
    public PageResult<PendingApplication> page(String cursor, String query, String status, int requestedSize) {
        int pageSize = Math.max(1, Math.min(requestedSize, 50));
        String normalized = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        String normalizedStatus = normalizeStatus(status);
        java.util.List<PendingApplication> values = jdbc.query("select a.id, a.display_name, a.introduction, a.portfolio_url, a.state, a.public_result, a.due_at, a.version, exists(select 1 from private_contact_records p where p.subject_type = 'PARTNER_APPLICATION' and p.subject_id = a.id and p.delete_after > current_timestamp(6)) contact_available from partner_applications a where (? is null or a.state = ?) and (? is null or lower(a.display_name) like ? or lower(a.introduction) like ?) and (? is null or a.id < ?) order by a.id desc limit ?",
                (row, index) -> pending(row),
                normalizedStatus, normalizedStatus, normalized, normalized, normalized, cursor, cursor, pageSize + 1);
        return PageResult.from(values, pageSize, PendingApplication::id);
    }

    @Transactional
    public Provisioned approveApplication(String applicationId, String loginId, String reason, String actorId, String actorRole, String requestId) {
        if (accountLocks != null) accountLocks.requireActiveActor(actorId);
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_REASON_REQUIRED", "请填写批准合作方申请的原因。");
        String normalizedLogin = LoginIds.normalize(loginId);
        String displayName;
        try {
            displayName = jdbc.queryForObject("select display_name from partner_applications where id = ? and state in ('RECEIVED','PENDING_REVIEW','IN_REVIEW')", String.class, applicationId);
        } catch (EmptyResultDataAccessException missing) {
            throw new ApiException(HttpStatus.CONFLICT, "PARTNER_APPLICATION_NOT_ACTIONABLE", "该合作方申请不存在或已被处理。");
        }
        if (accounts.findByLoginId(normalizedLogin).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_ALREADY_EXISTS", "该受控登录名已被使用。");
        Role partnerRole = roles.findByCode("PARTNER").orElseGet(() -> roles.save(new Role("PARTNER")));
        Account account = new Account(Ids.ulid(), normalizedLogin, "!ACTIVATION_REQUIRED!", "PENDING");
        account.addRole(partnerRole);
        try {
            accounts.saveAndFlush(account);
        } catch (org.springframework.dao.DataIntegrityViolationException error) {
            throw new ApiException(HttpStatus.CONFLICT, "LOGIN_ID_ALREADY_EXISTS", "该受控登录名已被使用。");
        }
        PartnerProfile profile = profiles.saveAndFlush(new PartnerProfile(Ids.ulid(), account.getId(), displayName, 3));
        String from = jdbc.query("select state from partner_applications where id = ? for update",
                result -> result.next() ? result.getString(1) : null, applicationId);
        int updated = jdbc.update("update partner_applications set state = 'ACTIVATION_PENDING', public_result = '申请已批准，激活凭据将由工作人员人工交付。', provisioned_account_id = ?, reviewed_by_account_id = ?, review_reason = ?, reviewed_at = current_timestamp(6), version = version + 1 where id = ? and state in ('RECEIVED','PENDING_REVIEW','IN_REVIEW')", account.getId(), actorId, normalizedReason, applicationId);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "PARTNER_APPLICATION_NOT_ACTIONABLE", "该合作方申请已被其他操作处理。 ");
        String token = activations.issueLocked(account).token();
        jdbc.update("insert into public_case_status_history (id, case_type, case_id, from_state, to_state, public_message, actor_account_id) values (?, 'PARTNER_APPLICATION', ?, ?, 'ACTIVATION_PENDING', '申请已批准，激活凭据将由工作人员人工交付。', ?)", Ids.ulid(), applicationId, from, actorId);
        audit.record(actorId, actorRole, "PARTNER_ACCOUNT_CREATED", "PARTNER_PROFILE", profile.getId(), "{}", "{\"applicationId\":\"" + applicationId + "\",\"qualificationStatus\":\"PENDING\"}", normalizedReason, requestId);
        audit.record(actorId, actorRole, "ACTIVATION_ISSUED", "ACCOUNT", account.getId(), "{}", "{\"qualificationStatus\":\"PENDING\"}", normalizedReason, requestId);
        return new Provisioned(profile.getId(), account.getLoginId(), token);
    }

    @Transactional
    public DecisionResult decideApplication(String applicationId, String decision, String reason, String actorId, String actorRole, String requestId) {
        if (accountLocks != null) accountLocks.requireActiveActor(actorId);
        if (!"REJECT".equals(decision)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARTNER_APPLICATION_DECISION", "合作方申请只能批准或拒绝；匿名申请不支持退回修改。 ");
        }
        String state = "REJECTED";
        String from = jdbc.query("select state from partner_applications where id = ? for update",
                result -> result.next() ? result.getString(1) : null, applicationId);
        int updated = jdbc.update("update partner_applications set state = ?, public_result = ?, reviewed_by_account_id = ?, review_reason = ?, reviewed_at = current_timestamp(6), resolved_at = current_timestamp(6), version = version + 1 where id = ? and state in ('RECEIVED','PENDING_REVIEW','IN_REVIEW')", state, reason, actorId, reason, applicationId);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "PARTNER_APPLICATION_NOT_ACTIONABLE", "该合作方申请不存在或已被处理。 ");
        audit.record(actorId, actorRole, "PARTNER_APPLICATION_" + decision, "PARTNER_APPLICATION", applicationId,
                "{\"state\":\"" + from + "\"}", "{\"state\":\"" + state + "\"}", reason, requestId);
        jdbc.update("insert into public_case_status_history (id, case_type, case_id, from_state, to_state, public_message, actor_account_id) values (?, 'PARTNER_APPLICATION', ?, ?, ?, ?, ?)", Ids.ulid(), applicationId, from, state, reason, actorId);
        return new DecisionResult(applicationId, state);
    }

    private String normalizeStatus(String status) {
        return PublicCaseStates.normalizePartnerFilter(status);
    }

    private PendingApplication pending(java.sql.ResultSet row) throws java.sql.SQLException {
        java.sql.Timestamp due = row.getTimestamp("due_at");
        return new PendingApplication(row.getString("id"), row.getString("display_name"), row.getString("introduction"), row.getString("portfolio_url"), row.getString("state"),
                row.getString("public_result"), due == null ? null : due.toInstant(), row.getLong("version"), row.getBoolean("contact_available"));
    }
}
