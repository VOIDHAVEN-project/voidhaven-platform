package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class RetentionService {
    private final JdbcTemplate jdbc;
    private final MediaOrphanCleanupService mediaOrphans;

    public RetentionService(JdbcTemplate jdbc, MediaOrphanCleanupService mediaOrphans) {
        this.jdbc = jdbc;
        this.mediaOrphans = mediaOrphans;
    }

    @Scheduled(cron = "${voidhaven.retention.cron:0 30 1 * * *}", zone = "Asia/Shanghai")
    public void scheduledRun() { run(); }

    @Transactional
    public void run() {
        int idempotency = jdbc.update("delete from idempotency_records where expires_at < current_timestamp(6)");
        int community = jdbc.update("delete from community_gate_events where expires_at < current_timestamp(6)");
        int contacts = jdbc.update("delete from private_contact_records where delete_after < current_timestamp(6)");
        int media = mediaOrphans.removeUnattachedAssets();
        int affected = idempotency + community + contacts + media;
        jdbc.update("insert into retention_runs (id, task_name, outcome, affected_count, detail_summary) values (?, ?, ?, ?, ?)", Ids.ulid(), "approved-retention-cleanup", "SUCCESS", affected, "idempotency=" + idempotency + ", community=" + community + ", privateContacts=" + contacts + ", orphanMedia=" + media);
    }
}
