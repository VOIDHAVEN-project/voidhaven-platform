package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class ManualDeliveryService {
    public record DeliveryRecord(String id, String subjectType, String subjectId, String deliveryType,
                                 Instant deliveredAt, String note) { }

    private static final Set<String> SUBJECTS = Set.of("ACCOUNT", "PARTNER_APPLICATION", "SUBMISSION");
    private static final Set<String> TYPES = Set.of("ACTIVATION", "PASSWORD_RESET", "STATUS_RESULT");

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ManualDeliveryService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public DeliveryRecord record(String subjectType, String subjectId, String deliveryType, String actorId,
                                 String note, String role, String requestId) {
        String type = requireMember(subjectType, SUBJECTS, "MANUAL_DELIVERY_SUBJECT_INVALID", "人工交付对象类型无效。");
        String delivery = requireMember(deliveryType, TYPES, "MANUAL_DELIVERY_TYPE_INVALID", "人工交付类型无效。");
        if (subjectId == null || subjectId.length() != 26) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MANUAL_DELIVERY_SUBJECT_INVALID", "人工交付对象无效。");
        }
        String normalizedNote = note == null || note.isBlank() ? null : note.trim();
        if (normalizedNote != null && normalizedNote.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MANUAL_DELIVERY_NOTE_INVALID", "交付说明不能超过 1000 个字符。");
        }
        validateDelivery(type, subjectId, delivery);
        String id = Ids.ulid();
        jdbc.update("""
                insert into manual_delivery_records
                  (id, subject_type, subject_id, delivery_type, delivered_by_account_id, note)
                values (?, ?, ?, ?, ?, ?)
                """, id, type, subjectId, delivery, actorId, normalizedNote);
        if ("PARTNER_APPLICATION".equals(type) && "ACTIVATION".equals(delivery)) {
            jdbc.update("update partner_applications set activation_delivered_at = current_timestamp(6) where id = ?", subjectId);
        }
        audit.record(actorId, role == null ? "SUPER_ADMIN" : role, "MANUAL_DELIVERY_RECORDED", type, subjectId,
                "{}", "{\"deliveryType\":\"" + delivery + "\"}", normalizedNote, requestId);
        return new DeliveryRecord(id, type, subjectId, delivery, Instant.now(), normalizedNote);
    }

    @Transactional(readOnly = true)
    public List<DeliveryRecord> list(String subjectType, String subjectId) {
        String type = subjectType == null || subjectType.isBlank() ? null : requireMember(subjectType, SUBJECTS, "MANUAL_DELIVERY_SUBJECT_INVALID", "人工交付对象类型无效。");
        String id = subjectId == null || subjectId.isBlank() ? null : subjectId;
        return jdbc.query("""
                select id, subject_type, subject_id, delivery_type, delivered_at, note
                from manual_delivery_records
                where (? is null or subject_type = ?) and (? is null or subject_id = ?)
                order by delivered_at desc, id desc
                limit 50
                """, (row, index) -> new DeliveryRecord(row.getString("id"), row.getString("subject_type"),
                row.getString("subject_id"), row.getString("delivery_type"),
                row.getTimestamp("delivered_at").toInstant(), row.getString("note")), type, type, id, id);
    }

    private String requireMember(String value, Set<String> allowed, String code, String message) {
        if (value == null || !allowed.contains(value)) throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        return value;
    }

    private void validateDelivery(String subjectType, String subjectId, String deliveryType) {
        boolean combinationAllowed = switch (deliveryType) {
            case "PASSWORD_RESET" -> "ACCOUNT".equals(subjectType);
            case "ACTIVATION" -> "ACCOUNT".equals(subjectType) || "PARTNER_APPLICATION".equals(subjectType);
            case "STATUS_RESULT" -> "SUBMISSION".equals(subjectType) || "PARTNER_APPLICATION".equals(subjectType);
            default -> false;
        };
        if (!combinationAllowed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MANUAL_DELIVERY_COMBINATION_INVALID", "该对象不支持这种人工交付类型。");
        }
        String table = switch (subjectType) {
            case "ACCOUNT" -> "accounts";
            case "PARTNER_APPLICATION" -> "partner_applications";
            case "SUBMISSION" -> "content_submissions";
            default -> throw new IllegalStateException("Unsupported manual-delivery subject");
        };
        Integer count;
        if ("STATUS_RESULT".equals(deliveryType)) {
            count = jdbc.queryForObject("select count(*) from " + table + " where id = ? and public_result is not null and trim(public_result) <> ''", Integer.class, subjectId);
        } else if ("PARTNER_APPLICATION".equals(subjectType)) {
            count = jdbc.queryForObject("select count(*) from partner_applications where id = ? and state = 'ACTIVATION_PENDING'", Integer.class, subjectId);
        } else if ("ACTIVATION".equals(deliveryType)) {
            count = jdbc.queryForObject("select count(*) from accounts a where a.id = ? and a.qualification_status = 'PENDING' and exists (select 1 from activation_tokens t where t.account_id = a.id and t.token_type = 'ACTIVATION' and t.consumed_at is null and t.expires_at > current_timestamp(6))", Integer.class, subjectId);
        } else {
            count = jdbc.queryForObject("select count(*) from accounts a where a.id = ? and a.qualification_status = 'ACTIVE' and exists (select 1 from activation_tokens t where t.account_id = a.id and t.token_type = 'PASSWORD_RESET' and t.consumed_at is null and t.expires_at > current_timestamp(6))", Integer.class, subjectId);
        }
        if (count == null || count != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "MANUAL_DELIVERY_NOT_READY", "交付对象不存在，或尚未形成可交付的凭据/用户可见结果。");
        }
    }
}
