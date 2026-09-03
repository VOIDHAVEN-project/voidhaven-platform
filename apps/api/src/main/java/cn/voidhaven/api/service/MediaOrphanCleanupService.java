package cn.voidhaven.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Removes media that is no longer attached to any revision.  Public reads are
 * association-based, so an unreferenced asset is never exposed while this
 * cleanup is waiting for its next run.
 */
@Service
public class MediaOrphanCleanupService {
    private final JdbcTemplate jdbc;
    private final MediaStorage storage;

    public MediaOrphanCleanupService(JdbcTemplate jdbc, MediaStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Transactional
    public int removeUnattachedAssets() {
        List<Orphan> orphans = jdbc.query(
                "select ma.id, ma.storage_key from media_assets ma where not exists (select 1 from revision_media rm where rm.media_asset_id = ma.id)",
                (row, index) -> new Orphan(row.getString("id"), row.getString("storage_key")));
        int removed = 0;
        for (Orphan orphan : orphans) {
            String confirmedKey = jdbc.query(
                    "select storage_key from media_assets where id = ? and not exists (select 1 from revision_media where media_asset_id = ?) for update",
                    result -> result.next() ? result.getString(1) : null,
                    orphan.id(), orphan.id());
            if (confirmedKey == null) continue;
            // The row lock prevents an association from being created between
            // this confirmation and the delete, so public media cannot be
            // removed after it has been attached.
            storage.delete(confirmedKey);
            removed += jdbc.update("delete from media_assets where id = ?", orphan.id());
        }
        return removed;
    }

    record Orphan(String id, String storageKey) { }
}
