package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class RetentionService {
    static final String TASK_LOCK = "approved-retention-cleanup";
    private final JdbcTemplate jdbc;
    private final MediaOrphanCleanupService mediaOrphans;
    private final MysqlAdvisoryLock advisoryLocks;

    RetentionService(JdbcTemplate jdbc, MediaOrphanCleanupService mediaOrphans) {
        this(jdbc, mediaOrphans, null);
    }

    @Autowired
    public RetentionService(JdbcTemplate jdbc, MediaOrphanCleanupService mediaOrphans, MysqlAdvisoryLock advisoryLocks) {
        this.jdbc = jdbc;
        this.mediaOrphans = mediaOrphans;
        this.advisoryLocks = advisoryLocks;
    }

    @Scheduled(cron = "${voidhaven.retention.cron:0 30 1 * * *}", zone = "Asia/Shanghai")
    public void scheduledRun() {
        if (advisoryLocks == null) {
            run();
            return;
        }
        advisoryLocks.runExclusive(TASK_LOCK, this::run);
    }

    public void run() {
        try {
            int idempotency = jdbc.update("delete from idempotency_records where expires_at < current_timestamp(6)");
            int community = jdbc.update("delete from community_gate_events where expires_at < current_timestamp(6)");
            int grants = jdbc.update("delete from community_access_grants where expires_at < current_timestamp(6)");
            int expiredInvites = jdbc.update("update community_invite_batches set state = 'EXPIRED' where state = 'ACTIVE' and expires_at < current_timestamp(6)");
            int contacts = jdbc.update("delete from private_contact_records where delete_after < current_timestamp(6)");
            int authRateLimits = jdbc.update("delete from auth_rate_limit_events where expires_at < current_timestamp(6)");
            int statusQueryRateLimits = jdbc.update("delete from public_status_query_rate_limit_events where expires_at < current_timestamp(6)");
            int publicWriteLimits = jdbc.update("delete from public_write_rate_limit_events where expires_at < current_timestamp(6)");
            int tokens = jdbc.update("delete from activation_tokens where expires_at < current_timestamp(6) or (consumed_at is not null and consumed_at < date_sub(current_timestamp(6), interval 90 day))");
            int history = jdbc.update("""
                    delete from public_case_status_history
                    where created_at < date_sub(current_timestamp(6), interval 180 day)
                      and (
                        (case_type = 'SUBMISSION' and exists (
                          select 1 from content_submissions s
                          where s.id = public_case_status_history.case_id
                            and s.state in ('RESOLVED','REJECTED','CLOSED')
                            and s.resolved_at is not null
                            and s.resolved_at < date_sub(current_timestamp(6), interval 180 day)))
                        or
                        (case_type = 'PARTNER_APPLICATION' and exists (
                          select 1 from partner_applications a
                          where a.id = public_case_status_history.case_id
                            and a.state in ('ACTIVATED','REJECTED','CLOSED')
                            and coalesce(a.resolved_at, a.activated_at) is not null
                            and coalesce(a.resolved_at, a.activated_at) < date_sub(current_timestamp(6), interval 180 day)))
                      )
                    """);
            int deliveries = jdbc.update("delete from manual_delivery_records where delivered_at < date_sub(current_timestamp(6), interval 730 day)");
            int media = mediaOrphans.removeUnattachedAssets();
            int affected = idempotency + community + grants + expiredInvites + contacts + authRateLimits
                    + statusQueryRateLimits + publicWriteLimits + tokens + history + deliveries + media;
            jdbc.update("insert into retention_runs (id, task_name, outcome, affected_count, detail_summary) values (?, ?, ?, ?, ?)", Ids.ulid(), TASK_LOCK, "SUCCESS", affected, "idempotency=" + idempotency + ", community=" + community + ", grants=" + grants + ", expiredInvites=" + expiredInvites + ", privateContacts=" + contacts + ", authRateLimits=" + authRateLimits + ", statusQueryRateLimits=" + statusQueryRateLimits + ", publicWriteLimits=" + publicWriteLimits + ", activationTokens=" + tokens + ", caseHistory=" + history + ", manualDeliveries=" + deliveries + ", orphanMedia=" + media);
        } catch (RuntimeException error) {
            jdbc.update("insert into retention_runs (id, task_name, outcome, affected_count, detail_summary) values (?, ?, ?, ?, ?)",
                    Ids.ulid(), TASK_LOCK, "FAILED", 0, error.getClass().getSimpleName());
            throw error;
        }
    }
}
