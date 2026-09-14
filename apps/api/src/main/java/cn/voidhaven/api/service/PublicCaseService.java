package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.support.SecureTokens;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PublicCaseService {
    public enum SubmissionType { CONTENT, FEEDBACK, COPYRIGHT }
    public record SubmissionCommand(SubmissionType submissionType, String title, String message, String linkUrl,
                                    String imageDescription, String contact) { }
    public record PartnerApplicationCommand(String displayName, String introduction, String portfolioUrl, String contact) { }
    public record CreatedCase(String id, String queryCode) { }
    public record CaseStatus(String id, String status, String publicResult, Instant dueAt, Instant createdAt, Instant updatedAt) { }

    private static final int CONTACT_RETENTION_DAYS = 180;
    private final JdbcTemplate jdbc;
    private final IdempotencyService idempotency;
    private final PrivateValueCipher privateValues;

    public PublicCaseService(JdbcTemplate jdbc, IdempotencyService idempotency, PrivateValueCipher privateValues) {
        this.jdbc = jdbc;
        this.idempotency = idempotency;
        this.privateValues = privateValues;
    }

    public CreatedCase createSubmission(String idempotencyKey, SubmissionCommand command) {
        String contact = blankToNull(command.contact());
        if (command.submissionType() == SubmissionType.COPYRIGHT && contact == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTACT_REQUIRED", "版权请求必须提供可联系到你的方式。");
        }
        if (contact != null) privateValues.requireEnabled();
        return idempotency.executeSingleDelivery(idempotencyKey, command, () -> createSubmission(command, contact));
    }

    public CreatedCase createPartnerApplication(String idempotencyKey, PartnerApplicationCommand command) {
        String contact = blankToNull(command.contact());
        if (contact == null) throw new ApiException(HttpStatus.BAD_REQUEST, "CONTACT_REQUIRED", "合作申请必须提供联系方式。");
        privateValues.requireEnabled();
        return idempotency.executeSingleDelivery(idempotencyKey, command, () -> createPartnerApplication(command, contact));
    }

    @Transactional
    protected CreatedCase createSubmission(SubmissionCommand command, String contact) {
        String id = Ids.ulid();
        String queryCode = SecureTokens.randomUrlToken();
        jdbc.update("insert into content_submissions (id, submission_type, title, message, link_url, image_description, access_code_hash, state, due_at) values (?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?)",
                id, command.submissionType().name(), command.title().trim(), command.message().trim(), blankToNull(command.linkUrl()),
                blankToNull(command.imageDescription()), SecureTokens.sha256(queryCode), Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)));
        storeContact("SUBMISSION", id, contact);
        addHistory("SUBMISSION", id, null, "RECEIVED", "我们已收到你的提交。", null);
        return new CreatedCase(id, queryCode);
    }

    @Transactional
    protected CreatedCase createPartnerApplication(PartnerApplicationCommand command, String contact) {
        String id = Ids.ulid();
        String queryCode = SecureTokens.randomUrlToken();
        jdbc.update("insert into partner_applications (id, display_name, introduction, portfolio_url, access_code_hash, state, due_at) values (?, ?, ?, ?, ?, 'RECEIVED', ?)",
                id, command.displayName().trim(), command.introduction().trim(), blankToNull(command.portfolioUrl()),
                SecureTokens.sha256(queryCode), Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS)));
        storeContact("PARTNER_APPLICATION", id, contact);
        addHistory("PARTNER_APPLICATION", id, null, "RECEIVED", "我们已收到你的合作申请。", null);
        return new CreatedCase(id, queryCode);
    }

    @Transactional(readOnly = true)
    public CaseStatus submissionStatus(String id, String queryCode) {
        return status("content_submissions", id, queryCode);
    }

    @Transactional(readOnly = true)
    public CaseStatus partnerApplicationStatus(String id, String queryCode) {
        return status("partner_applications", id, queryCode);
    }

    private CaseStatus status(String table, String id, String queryCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, access_code_hash, state, public_result, due_at, created_at, updated_at from " + table + " where id = ?", id);
        if (rows.isEmpty() || queryCode == null || queryCode.isBlank()) throw invalidQuery();
        Map<String, Object> row = rows.get(0);
        String expected = Objects.toString(row.get("access_code_hash"), null);
        if (!SecureTokens.constantTimeEquals(expected, SecureTokens.sha256(queryCode))) throw invalidQuery();
        return new CaseStatus(
                Objects.toString(row.get("id")), Objects.toString(row.get("state")),
                Objects.toString(row.get("public_result"), null), instant(row.get("due_at")),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private void storeContact(String subjectType, String subjectId, String contact) {
        if (contact == null) return;
        PrivateValueCipher.EncryptedValue encrypted = privateValues.encrypt(contact, subjectType + ":" + subjectId);
        jdbc.update("insert into private_contact_records (id, subject_type, subject_id, ciphertext, key_id, delete_after) values (?, ?, ?, ?, ?, ?)",
                Ids.ulid(), subjectType, subjectId, encrypted.ciphertext(), encrypted.keyId(),
                Timestamp.from(Instant.now().plus(CONTACT_RETENTION_DAYS, ChronoUnit.DAYS)));
    }

    private void addHistory(String type, String id, String from, String to, String message, String actorId) {
        jdbc.update("insert into public_case_status_history (id, case_type, case_id, from_state, to_state, public_message, actor_account_id) values (?, ?, ?, ?, ?, ?, ?)",
                Ids.ulid(), type, id, from, to, message, actorId);
    }

    private Instant instant(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toInstant() : null;
    }

    private ApiException invalidQuery() {
        return new ApiException(HttpStatus.NOT_FOUND, "CASE_QUERY_INVALID", "回执号或查询码无效。");
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
