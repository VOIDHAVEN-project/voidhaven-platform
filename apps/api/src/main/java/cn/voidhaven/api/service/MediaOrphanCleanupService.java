package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MediaOrphanCleanupService {
    private final JdbcTemplate jdbc;
    private final MediaStorage storage;
    private final String instanceId;

    @Autowired
    public MediaOrphanCleanupService(JdbcTemplate jdbc, MediaStorage storage) {
        this(jdbc, storage, Ids.ulid());
    }

    MediaOrphanCleanupService(JdbcTemplate jdbc, MediaStorage storage, String instanceId) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.instanceId = instanceId;
    }

    @Transactional
    public int queueUnattachedAssets() {
        List<Orphan> orphans = jdbc.query(
                "select ma.id, ma.storage_key from media_assets ma where ma.lifecycle_state = 'ACTIVE' and ma.storage_key not like 'community/%' and not exists (select 1 from revision_media rm where rm.media_asset_id = ma.id) and not exists (select 1 from community_entries ce where ce.media_asset_id = ma.id) and not exists (select 1 from content_action_entries ca where ca.media_asset_id = ma.id) and not exists (select 1 from content_action_media_reservations r where r.media_asset_id = ma.id and r.consumed_at is null and r.expires_at > current_timestamp(6)) for update",
                (row, index) -> new Orphan(row.getString("id"), row.getString("storage_key")));
        int queued = 0;
        for (Orphan orphan : orphans) {
            int marked = jdbc.update("update media_assets set lifecycle_state = 'DELETION_PENDING' where id = ? and lifecycle_state = 'ACTIVE' and storage_key not like 'community/%' and not exists (select 1 from revision_media where media_asset_id = ?) and not exists (select 1 from community_entries where media_asset_id = ?) and not exists (select 1 from content_action_entries where media_asset_id = ?) and not exists (select 1 from content_action_media_reservations where media_asset_id = ? and consumed_at is null and expires_at > current_timestamp(6))", orphan.id(), orphan.id(), orphan.id(), orphan.id(), orphan.id());
            if (marked == 0) continue;
            jdbc.update("insert into media_deletion_jobs (id, media_asset_id, storage_key) values (?, ?, ?)", Ids.ulid(), orphan.id(), orphan.storageKey());
            queued++;
        }
        return queued;
    }

    public int processDueDeletions() {
        String claimId = instanceId + ":" + Ids.ulid();
        jdbc.update("""
                update media_deletion_jobs
                   set locked_by = ?, locked_until = date_add(current_timestamp(6), interval 10 minute)
                 where state in ('PENDING','FAILED')
                   and next_attempt_at <= current_timestamp(6)
                   and (locked_until is null or locked_until < current_timestamp(6))
                 order by created_at asc
                 limit 100
                """, claimId);
        List<DeletionJob> jobs = jdbc.query(
                "select id, media_asset_id, storage_key from media_deletion_jobs where locked_by = ? and state in ('PENDING','FAILED') and locked_until > current_timestamp(6)",
                (row, index) -> new DeletionJob(row.getString("id"), row.getString("media_asset_id"), row.getString("storage_key")),
                claimId);
        int completed = 0;
        for (DeletionJob job : jobs) {
            try {
                storage.delete(job.storageKey());
                jdbc.update("update media_assets set lifecycle_state = 'DELETED' where id = ? and lifecycle_state = 'DELETION_PENDING'", job.mediaAssetId());
                int updated = jdbc.update("update media_deletion_jobs set state = 'SUCCEEDED', attempts = attempts + 1, last_error = null, completed_at = current_timestamp(6), locked_by = null, locked_until = null where id = ? and locked_by = ?", job.id(), claimId);
                if (updated == 1) completed++;
            } catch (RuntimeException error) {
                String summary = error.getClass().getSimpleName();
                jdbc.update("update media_deletion_jobs set state = 'FAILED', attempts = attempts + 1, last_error = ?, next_attempt_at = date_add(current_timestamp(6), interval 5 minute), locked_by = null, locked_until = null where id = ? and locked_by = ?", summary, job.id(), claimId);
            }
        }
        return completed;
    }

    public int removeUnattachedAssets() {
        queueUnattachedAssets();
        return processDueDeletions();
    }

    record Orphan(String id, String storageKey) { }
    record DeletionJob(String id, String mediaAssetId, String storageKey) { }
}
