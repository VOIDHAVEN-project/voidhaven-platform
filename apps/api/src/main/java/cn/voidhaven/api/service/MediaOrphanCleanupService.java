package cn.voidhaven.api.service;

import cn.voidhaven.api.support.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MediaOrphanCleanupService {
    private final JdbcTemplate jdbc;
    private final MediaStorage storage;

    public MediaOrphanCleanupService(JdbcTemplate jdbc, MediaStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Transactional
    public int queueUnattachedAssets() {
        List<Orphan> orphans = jdbc.query(
                "select ma.id, ma.storage_key from media_assets ma where ma.lifecycle_state = 'ACTIVE' and not exists (select 1 from revision_media rm where rm.media_asset_id = ma.id) for update",
                (row, index) -> new Orphan(row.getString("id"), row.getString("storage_key")));
        int queued = 0;
        for (Orphan orphan : orphans) {
            int marked = jdbc.update("update media_assets set lifecycle_state = 'DELETION_PENDING' where id = ? and lifecycle_state = 'ACTIVE' and not exists (select 1 from revision_media where media_asset_id = ?)", orphan.id(), orphan.id());
            if (marked == 0) continue;
            jdbc.update("insert into media_deletion_jobs (id, media_asset_id, storage_key) values (?, ?, ?)", Ids.ulid(), orphan.id(), orphan.storageKey());
            queued++;
        }
        return queued;
    }

    public int processDueDeletions() {
        List<DeletionJob> jobs = jdbc.query(
                "select id, media_asset_id, storage_key from media_deletion_jobs where state in ('PENDING','FAILED') and next_attempt_at <= current_timestamp(6) order by created_at asc limit 100",
                (row, index) -> new DeletionJob(row.getString("id"), row.getString("media_asset_id"), row.getString("storage_key")));
        int completed = 0;
        for (DeletionJob job : jobs) {
            try {
                storage.delete(job.storageKey());
                jdbc.update("update media_assets set lifecycle_state = 'DELETED' where id = ? and lifecycle_state = 'DELETION_PENDING'", job.mediaAssetId());
                jdbc.update("update media_deletion_jobs set state = 'SUCCEEDED', attempts = attempts + 1, last_error = null, completed_at = current_timestamp(6) where id = ?", job.id());
                completed++;
            } catch (RuntimeException error) {
                String summary = error.getClass().getSimpleName();
                jdbc.update("update media_deletion_jobs set state = 'FAILED', attempts = attempts + 1, last_error = ?, next_attempt_at = date_add(current_timestamp(6), interval 5 minute) where id = ?", summary, job.id());
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
