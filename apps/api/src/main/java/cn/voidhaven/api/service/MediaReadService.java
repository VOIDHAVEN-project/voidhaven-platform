package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import cn.voidhaven.api.web.PublicMediaDto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MediaReadService {
    private final JdbcTemplate jdbc;
    private final MediaStorage storage;

    public MediaReadService(JdbcTemplate jdbc, MediaStorage storage) { this.jdbc = jdbc; this.storage = storage; }

    public List<PublicMediaDto> forPublishedRevision(String revisionId) {
        return jdbc.query("select media_asset_id, media_type, width_px, height_px, alt_text from revision_media join media_assets on media_assets.id = revision_media.media_asset_id where revision_id = ? order by sort_order asc", (row, index) -> new PublicMediaDto(row.getString("media_asset_id"), "/api/v1/public/media/" + row.getString("media_asset_id"), row.getString("media_type"), row.getInt("width_px"), row.getInt("height_px"), row.getString("alt_text")), revisionId);
    }

    public MediaStorage.StoredObject loadPublic(String assetId) {
        String key = jdbc.query("select media_assets.storage_key from media_assets join revision_media on revision_media.media_asset_id = media_assets.id join content_items on content_items.published_revision_id = revision_media.revision_id where media_assets.id = ? and content_items.visibility = 'PUBLIC' and content_items.record_state = 'ACTIVE'", result -> result.next() ? result.getString(1) : null, assetId);
        if (key == null) throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体不存在或当前不可见。");
        return storage.get(key);
    }
}
