package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PublicCaseAdministrationService {
    public record TransitionResult(String id, String status, String publicResult, Instant dueAt, long version) { }
    public record ContactView(String id, String contact) { }

    private static final Set<String> SUBMISSION_STATES = PublicCaseStates.SUBMISSION;
    private static final Set<String> PARTNER_STATES = PublicCaseStates.PARTNER;
    private final JdbcTemplate jdbc;
    private final PrivateValueCipher privateValues;
    private final AuditService audit;

    public PublicCaseAdministrationService(JdbcTemplate jdbc, PrivateValueCipher privateValues, AuditService audit) {
        this.jdbc = jdbc;
        this.privateValues = privateValues;
        this.audit = audit;
    }

    @Transactional
    public TransitionResult transitionSubmission(String id, String status, String publicResult, Instant dueAt, boolean clearDueAt, long expectedVersion,
                                                  String reason, String actorId, String role, String requestId) {
        return transition("content_submissions", "SUBMISSION", "CONTENT_SUBMISSION", SUBMISSION_STATES,
                id, status, publicResult, dueAt, clearDueAt, expectedVersion, reason, actorId, role, requestId);
    }

    @Transactional
    public TransitionResult transitionPartnerApplication(String id, String status, String publicResult, Instant dueAt, boolean clearDueAt, long expectedVersion,
                                                         String reason, String actorId, String role, String requestId) {
        return transition("partner_applications", "PARTNER_APPLICATION", "PARTNER_APPLICATION", PARTNER_STATES,
                id, status, publicResult, dueAt, clearDueAt, expectedVersion, reason, actorId, role, requestId);
    }

    @Transactional
    public ContactView revealSubmissionContact(String id, String reason, String actorId, String role, String requestId) {
        return reveal("SUBMISSION", "CONTENT_SUBMISSION", id, reason, actorId, role, requestId);
    }

    @Transactional
    public ContactView revealPartnerContact(String id, String reason, String actorId, String role, String requestId) {
        return reveal("PARTNER_APPLICATION", "PARTNER_APPLICATION", id, reason, actorId, role, requestId);
    }

    private TransitionResult transition(String table, String caseType, String objectType, Set<String> allowed,
                                        String id, String status, String publicResult, Instant dueAt, boolean clearDueAt, long expectedVersion,
                                        String reason, String actorId, String role, String requestId) {
        String target = status == null ? "" : status.trim();
        if (!allowed.contains(target)) throw new ApiException(HttpStatus.BAD_REQUEST, "CASE_STATUS_INVALID", "处理状态无效。");
        Map<String, Object> before = row(table, id);
        String from = Objects.toString(before.get("state"));
        if (dueAt != null && clearDueAt) throw new ApiException(HttpStatus.BAD_REQUEST, "CASE_DUE_AT_CONFLICT", "不能同时设置和清除处理截止时间。");
        Timestamp previousDueAt = (Timestamp) before.get("due_at");
        Instant effectiveDueAt = clearDueAt ? null : dueAt != null ? dueAt : previousDueAt == null ? null : previousDueAt.toInstant();
        if (isTerminal(from) && !from.equals(target)) throw new ApiException(HttpStatus.CONFLICT, "CASE_ALREADY_TERMINAL", "该事项已结束，不能再次变更状态。");
        int updated = jdbc.update("update " + table + " set state = ?, public_result = ?, due_at = ?, assignee_account_id = ?, resolved_at = case when ? in ('RESOLVED','REJECTED','CLOSED','ACTIVATED') then current_timestamp(6) else null end, version = version + 1 where id = ? and version = ?",
                target, blankToNull(publicResult), effectiveDueAt == null ? null : Timestamp.from(effectiveDueAt), actorId, target, id, expectedVersion);
        if (updated != 1) throw new ApiException(HttpStatus.CONFLICT, "CASE_VERSION_CONFLICT", "该事项已被其他操作更新，请刷新后重试。");
        jdbc.update("insert into public_case_status_history (id, case_type, case_id, from_state, to_state, public_message, actor_account_id) values (?, ?, ?, ?, ?, ?, ?)",
                Ids.ulid(), caseType, id, from, target, blankToNull(publicResult), actorId);
        audit.record(actorId, role, "PUBLIC_CASE_TRANSITIONED", objectType, id,
                "{\"state\":\"" + from + "\",\"dueAt\":" + quote(previousDueAt == null ? null : previousDueAt.toInstant().toString()) + "}",
                "{\"state\":\"" + target + "\",\"dueAt\":" + quote(effectiveDueAt == null ? null : effectiveDueAt.toString()) + "}", reason, requestId);
        return new TransitionResult(id, target, blankToNull(publicResult), effectiveDueAt, expectedVersion + 1);
    }

    private ContactView reveal(String subjectType, String objectType, String id, String reason, String actorId, String role, String requestId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select ciphertext, key_id from private_contact_records where subject_type = ? and subject_id = ? and delete_after > current_timestamp(6) order by created_at desc limit 1", subjectType, id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "PRIVATE_CONTACT_NOT_FOUND", "该事项没有可用的联系人记录。");
        Map<String, Object> row = rows.get(0);
        String contact = privateValues.decrypt(Objects.toString(row.get("ciphertext")), Objects.toString(row.get("key_id")), subjectType + ":" + id);
        audit.record(actorId, role, "PRIVATE_CONTACT_REVEALED", objectType, id, "{}", "{\"revealed\":true}", reason, requestId);
        return new ContactView(id, contact);
    }

    private Map<String, Object> row(String table, String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select state, due_at, version from " + table + " where id = ? for update", id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "事项不存在。");
        return rows.get(0);
    }

    private boolean isTerminal(String state) { return Set.of("RESOLVED", "REJECTED", "CLOSED", "ACTIVATED").contains(state); }
    private String quote(String value) { return value == null ? "null" : "\"" + value.replace("\"", "\\\"") + "\""; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
