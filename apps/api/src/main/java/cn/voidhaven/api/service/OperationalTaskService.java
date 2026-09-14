package cn.voidhaven.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationalTaskService {
    public record Task(String code, String label, int count, String href, String severity) { }
    private final JdbcTemplate jdbc;
    public OperationalTaskService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Task> tasks() {
        return List.of(
                task("CONTENT_REVIEW", "待审核内容", count("select count(*) from content_items where workflow_status = 'PENDING_REVIEW' and record_state = 'ACTIVE'"), "/workspace?section=reviews", "normal"),
                task("PUBLIC_INPUT", "待处理投稿与反馈", count("select count(*) from content_submissions where state in ('RECEIVED','PENDING_REVIEW','IN_PROGRESS','WAITING_FOR_REQUESTER') and submission_type <> 'COPYRIGHT'"), "/workspace?section=submissions", "normal"),
                task("COPYRIGHT", "待处理版权请求", count("select count(*) from content_submissions where state in ('RECEIVED','PENDING_REVIEW','IN_PROGRESS','WAITING_FOR_REQUESTER') and submission_type = 'COPYRIGHT'"), "/workspace?section=submissions&type=COPYRIGHT", "urgent"),
                task("PARTNER_APPLICATION", "待处理合作申请", count("select count(*) from partner_applications where state in ('RECEIVED','PENDING_REVIEW','IN_REVIEW','WAITING_FOR_APPLICANT','ACTIVATION_PENDING')"), "/workspace?section=applications", "normal"),
                task("PENDING_ACTIVATION", "待人工交付激活凭据", count("select count(*) from accounts where qualification_status = 'PENDING'"), "/workspace?section=accounts&status=PENDING", "normal"),
                task("EXPIRING_CONTENT", "七天内截止内容", count("select count(*) from content_items c left join competition_revision_details cd on cd.revision_id = c.published_revision_id left join recruitment_revision_details rd on rd.revision_id = c.published_revision_id where c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE' and coalesce(cd.deadline_at, rd.deadline_at) between current_timestamp(6) and date_add(current_timestamp(6), interval 7 day)"), "/workspace?section=content&deadline=7d", "normal"),
                task("INVALID_LINK", "链接待复核内容", count("select count(*) from content_verification_records v join (select content_item_id, max(checked_at) checked_at from content_verification_records group by content_item_id) latest on latest.content_item_id = v.content_item_id and latest.checked_at = v.checked_at where v.outcome in ('INVALID','NEEDS_REVIEW')"), "/workspace?section=content&verification=attention", "urgent"),
                task("EXPIRING_INVITE", "七天内到期邀请码", count("select count(*) from community_invite_batches where state = 'ACTIVE' and expires_at between current_timestamp(6) and date_add(current_timestamp(6), interval 7 day)"), "/workspace?section=community", "normal")
        );
    }
    private Task task(String code, String label, int count, String href, String severity) { return new Task(code, label, count, href, severity); }
    private int count(String sql) { Integer value = jdbc.queryForObject(sql, Integer.class); return value == null ? 0 : value; }
}
