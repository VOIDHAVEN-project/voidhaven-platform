package cn.voidhaven.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationalTaskService {
    public record Task(String code, String label, int count, String href, String severity) { }
    private final JdbcTemplate jdbc;
    private AdminAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    void authorization(AdminAuthorizationService authorization) { this.authorization = authorization; }
    public OperationalTaskService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Task> tasks() {
        if (authorization != null && !authorization.isSuper()) return authorizedTasks();
        return List.of(
                task("CONTENT_REVIEW", "待审核内容", count("select count(*) from content_items where workflow_status = 'PENDING_REVIEW' and record_state = 'ACTIVE'"), "/workspace?section=reviews", "normal"),
                task("PUBLIC_INPUT", "待处理投稿与反馈", count("select count(*) from content_submissions where state in ('RECEIVED','PENDING_REVIEW','IN_PROGRESS','WAITING_FOR_REQUESTER') and submission_type <> 'COPYRIGHT'"), "/workspace?section=submissions", "normal"),
                task("COPYRIGHT", "待处理版权请求", count("select count(*) from content_submissions where state in ('RECEIVED','PENDING_REVIEW','IN_PROGRESS','WAITING_FOR_REQUESTER') and submission_type = 'COPYRIGHT'"), "/workspace?section=submissions&type=COPYRIGHT", "urgent"),
                task("PARTNER_APPLICATION", "待处理合作申请", count("select count(*) from partner_applications where state in ('RECEIVED','PENDING_REVIEW','IN_REVIEW','WAITING_FOR_APPLICANT','ACTIVATION_PENDING')"), "/workspace?section=applications", "normal"),
                task("PENDING_ACTIVATION", "待人工交付激活凭据", count("select count(*) from accounts where qualification_status = 'PENDING'"), "/workspace?section=accounts&status=PENDING", "normal"),
                task("EXPIRING_CONTENT", "七天内截止内容", count("select count(*) from content_items c left join competition_revision_details cd on cd.revision_id = c.published_revision_id left join recruitment_revision_details rd on rd.revision_id = c.published_revision_id where c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE' and coalesce(cd.deadline_at, rd.deadline_at) between current_timestamp(6) and date_add(current_timestamp(6), interval 7 day)"), "/workspace?section=content&deadline=7d", "normal"),
                task("VERIFICATION_ATTENTION", "核验异常内容", count("select count(*) from content_verification_records v join content_items c on c.id = v.content_item_id where c.record_state = 'ACTIVE' and v.outcome in ('INVALID','NEEDS_REVIEW') and not exists (select 1 from content_verification_records newer where newer.content_item_id = v.content_item_id and (newer.checked_at > v.checked_at or (newer.checked_at = v.checked_at and newer.id > v.id)))"), "/workspace?section=content&verification=attention", "urgent"),
                task("VERIFICATION_DUE", "定期复核已到期", count("select count(*) from content_verification_records v join content_items c on c.id = v.content_item_id where c.record_state = 'ACTIVE' and v.outcome = 'VALID' and v.next_check_at is not null and v.next_check_at <= current_timestamp(6) and not exists (select 1 from content_verification_records newer where newer.content_item_id = v.content_item_id and (newer.checked_at > v.checked_at or (newer.checked_at = v.checked_at and newer.id > v.id)))"), "/workspace?section=content&verification=due", "normal"),
                task("EXPIRING_INVITE", "七天内到期邀请码", count("select count(*) from community_invite_batches where state = 'ACTIVE' and expires_at between current_timestamp(6) and date_add(current_timestamp(6), interval 7 day)"), "/workspace?section=community", "normal")
        );
    }
    private Task task(String code, String label, int count, String href, String severity) { return new Task(code, label, count, href, severity); }
    private List<Task> authorizedTasks() {
        var tasks = new java.util.ArrayList<Task>();
        if (authorization.has("CONTENT_REVIEW")) tasks.add(task("CONTENT_REVIEW", "待审核内容", scopedCount("CONTENT_REVIEW", "c.workflow_status = 'PENDING_REVIEW' and c.record_state = 'ACTIVE'"), "/workspace?section=reviews", "normal"));
        if (authorization.has("CONTENT_VERIFY")) tasks.add(task("VERIFICATION_ATTENTION", "需核验内容", scopedCount("CONTENT_VERIFY", "c.record_state = 'ACTIVE' and exists(select 1 from content_verification_records v where v.content_item_id = c.id and v.outcome in ('INVALID','NEEDS_REVIEW') and not exists(select 1 from content_verification_records n where n.content_item_id = v.content_item_id and (n.checked_at > v.checked_at or (n.checked_at = v.checked_at and n.id > v.id))))"), "/workspace?section=content&verification=attention", "urgent"));
        if (authorization.has("PUBLIC_INPUT_OPERATE")) tasks.add(task("PUBLIC_INPUT", "待处理投稿与反馈", count("select count(*) from content_submissions where state in ('RECEIVED','PENDING_REVIEW','IN_PROGRESS','WAITING_FOR_REQUESTER')"), "/workspace?section=submissions", "normal"));
        if (authorization.has("PARTNER_VERIFY")) tasks.add(task("PARTNER_APPLICATION", "待处理合作申请", count("select count(*) from partner_applications where state in ('RECEIVED','PENDING_REVIEW','IN_REVIEW','WAITING_FOR_APPLICANT','ACTIVATION_PENDING')"), "/workspace?section=applications", "normal"));
        if (authorization.has("ACCOUNT_OPERATE")) {
            int pending = 0;
            for (String id : jdbc.queryForList("select id from accounts where qualification_status = 'PENDING'", String.class)) {
                try { authorization.requirePartnerAccount(id); pending++; } catch (cn.voidhaven.api.web.ApiException ignored) { }
            }
            tasks.add(task("PENDING_ACTIVATION", "待人工交付激活凭据", pending, "/workspace?section=accounts&status=PENDING", "normal"));
        }
        return tasks;
    }
    private int scopedCount(String permission, String condition) {
        var arguments = new java.util.ArrayList<Object>();
        String scope = authorization.contentPredicate(permission, "c", arguments);
        Integer value = jdbc.queryForObject("select count(*) from content_items c where " + condition + " and " + scope, Integer.class, arguments.toArray());
        return value == null ? 0 : value;
    }
    private int count(String sql) { Integer value = jdbc.queryForObject(sql, Integer.class); return value == null ? 0 : value; }
}
