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
        return jdbc.query("select media_asset_id, media_type, width_px, height_px, alt_text from revision_media join media_assets on media_assets.id = revision_media.media_asset_id where revision_id = ? and media_assets.lifecycle_state = 'ACTIVE' order by sort_order asc", (row, index) -> new PublicMediaDto(row.getString("media_asset_id"), "/api/v1/public/media/" + row.getString("media_asset_id"), row.getString("media_type"), row.getInt("width_px"), row.getInt("height_px"), row.getString("alt_text")), revisionId);
    }

    public MediaStorage.StoredObject loadPublic(String assetId) {
        String key = jdbc.query("select ma.storage_key from media_assets ma where ma.id = ? and ma.lifecycle_state = 'ACTIVE' and (exists(select 1 from revision_media rm join content_items c on c.published_revision_id = rm.revision_id where rm.media_asset_id = ma.id and c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE') or exists(select 1 from content_action_entries ca join content_items c on c.published_revision_id = ca.revision_id where ca.media_asset_id = ma.id and ca.enabled = true and ca.access_level = 'PUBLIC' and c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE' and c.operational_status = 'OPEN') or exists(select 1 from community_entries ce where ce.media_asset_id = ma.id and ce.active = true and ce.access_level = 'PUBLIC'))", result -> result.next() ? result.getString(1) : null, assetId);
        if (key == null) throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体不存在或当前不可见。");
        return storage.get(key);
    }

    public MediaStorage.StoredObject loadManaged(String assetId, String actorAccountId, boolean administrator) {
        String key = jdbc.query("select ma.storage_key from media_assets ma where ma.id = ? and ma.lifecycle_state = 'ACTIVE' and (exists(select 1 from revision_media rm join content_items c on c.current_revision_id = rm.revision_id left join partner_profiles p on p.id = c.owner_partner_profile_id where rm.media_asset_id = ma.id and c.record_state = 'ACTIVE' and (? = true or (p.account_id = ? and p.active = true))) or exists(select 1 from content_action_entries ca join content_items c on c.current_revision_id = ca.revision_id left join partner_profiles p on p.id = c.owner_partner_profile_id where ca.media_asset_id = ma.id and c.record_state = 'ACTIVE' and (? = true or (p.account_id = ? and p.active = true))) or exists(select 1 from content_action_media_reservations r where r.media_asset_id = ma.id and r.expires_at > current_timestamp(6) and (? = true or r.owner_account_id = ?)))", result -> result.next() ? result.getString(1) : null, assetId, administrator, actorAccountId, administrator, actorAccountId, administrator, actorAccountId);
        if (key == null) throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体不存在或当前不可见。");
        return storage.get(key);
    }

    public MediaStorage.StoredObject loadCommunity(String assetId) {
        String key = jdbc.query("select ma.storage_key from media_assets ma where ma.id = ? and ma.lifecycle_state = 'ACTIVE' and (exists(select 1 from community_entries ce where ce.media_asset_id = ma.id and ce.active = true) or exists(select 1 from content_action_entries ca join content_items c on c.published_revision_id = ca.revision_id where ca.media_asset_id = ma.id and ca.enabled = true and ca.access_level = 'COMMUNITY_GATED' and c.visibility = 'PUBLIC' and c.record_state = 'ACTIVE' and c.operational_status = 'OPEN'))", result -> result.next() ? result.getString(1) : null, assetId);
        if (key == null) throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体不存在或当前不可见。");
        return storage.get(key);
    }
}
