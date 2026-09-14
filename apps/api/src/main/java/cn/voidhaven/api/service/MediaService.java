package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import cn.voidhaven.api.support.Ids;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MediaService {
    private static final int MAX_IMAGES_PER_REVISION = 10;
    private static final long MAX_TOTAL_BYTES_PER_REVISION = 20L * 1024 * 1024;
    private final ContentItemRepository items;
    private final PartnerProfileRepository profiles;
    private final JdbcTemplate jdbc;
    private final MediaStorage storage;
    private final MediaValidator validator;
    private final AuditService audit;
    private AccountLockService accountLocks;

    public MediaService(ContentItemRepository items, PartnerProfileRepository profiles, JdbcTemplate jdbc, MediaStorage storage, MediaValidator validator, AuditService audit) { this.items = items; this.profiles = profiles; this.jdbc = jdbc; this.storage = storage; this.validator = validator; this.audit = audit; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAccountLocks(AccountLockService accountLocks) { this.accountLocks = accountLocks; }

    public record Uploaded(String id, String url, String mediaType, int width, int height, String altText) { }

    @Transactional
    public Uploaded upload(String contentItemId, String altText, byte[] input, String actorId, String role, boolean administrator, String requestId) {
        if (accountLocks != null) accountLocks.requireActiveActor(actorId);
        if (altText == null || altText.isBlank() || altText.length() > 240) throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_ALT_TEXT_REQUIRED", "请提供不超过 240 字的图片说明。 ");
        MediaValidator.ValidatedImage image = validator.validate(input);
        ContentItem item = items.findByIdForUpdate(contentItemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。"));
        if (!administrator) {
            String profileId = profiles.findByAccountId(actorId).filter(profile -> profile.isActive()).map(profile -> profile.getId()).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "PARTNER_PROFILE_REQUIRED", "当前账号没有可用的合作方档案。"));
            if (!profileId.equals(item.getOwnerPartnerProfileId())) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。");
        }
        if (item.getRecordState() != RecordState.ACTIVE || item.getCurrentRevision() == null || (item.getWorkflowStatus() != WorkflowStatus.DRAFT && item.getWorkflowStatus() != WorkflowStatus.CHANGES_REQUESTED)) throw new ApiException(HttpStatus.CONFLICT, "CONTENT_MEDIA_NOT_EDITABLE", "只有可编辑草稿可添加正文图片。 ");
        String revisionId = item.getCurrentRevision().getId();
        Integer count = jdbc.queryForObject("select count(*) from revision_media where revision_id = ?", Integer.class, revisionId);
        Long total = jdbc.queryForObject("select coalesce(sum(media_assets.size_bytes), 0) from revision_media join media_assets on media_assets.id = revision_media.media_asset_id where revision_media.revision_id = ?", Long.class, revisionId);
        if (count != null && count >= MAX_IMAGES_PER_REVISION) throw new ApiException(HttpStatus.CONFLICT, "MEDIA_LIMIT_EXCEEDED", "每篇内容最多上传 10 张图片。 ");
        if ((total == null ? 0 : total) + image.bytes().length > MAX_TOTAL_BYTES_PER_REVISION) throw new ApiException(HttpStatus.CONFLICT, "MEDIA_LIMIT_EXCEEDED", "每篇内容的图片总量最多 20 MB。 ");
        String assetId = Ids.ulid();
        String key = "content/" + revisionId + "/" + assetId + "." + image.extension();
        storage.put(key, image.mediaType(), image.bytes());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) try { storage.delete(key); } catch (RuntimeException ignored) { }
                }
            });
        }
        try {
            jdbc.update("insert into media_assets (id, storage_key, media_type, size_bytes, width_px, height_px, owner_account_id) values (?, ?, ?, ?, ?, ?, ?)", assetId, key, image.mediaType(), image.bytes().length, image.width(), image.height(), actorId);
            jdbc.update("insert into revision_media (revision_id, media_asset_id, sort_order, alt_text) values (?, ?, ?, ?)", revisionId, assetId, count == null ? 0 : count, altText.trim());
            audit.record(actorId, role, "MEDIA_UPLOADED", "MEDIA_ASSET", assetId, "{}", "{\"revisionId\":\"" + revisionId + "\"}", null, requestId);
            return new Uploaded(assetId, "/api/v1/media/" + assetId + "/preview", image.mediaType(), image.width(), image.height(), altText.trim());
        } catch (RuntimeException error) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) try { storage.delete(key); } catch (RuntimeException ignored) { }
            throw error;
        }
    }

    @Transactional
    public Uploaded uploadCommunity(String altText, byte[] input, String actorId, String role, String requestId) {
        if (accountLocks != null) accountLocks.requireActiveActor(actorId);
        if (altText == null || altText.isBlank() || altText.length() > 240) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_ALT_TEXT_REQUIRED", "请提供不超过 240 字的图片说明。");
        }
        MediaValidator.ValidatedImage image = validator.validate(input);
        String assetId = Ids.ulid();
        String key = "community/" + assetId + "." + image.extension();
        storage.put(key, image.mediaType(), image.bytes());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) try { storage.delete(key); } catch (RuntimeException ignored) { }
                }
            });
        }
        try {
            jdbc.update("insert into media_assets (id, storage_key, media_type, size_bytes, width_px, height_px, owner_account_id) values (?, ?, ?, ?, ?, ?, ?)",
                    assetId, key, image.mediaType(), image.bytes().length, image.width(), image.height(), actorId);
            audit.record(actorId, role, "COMMUNITY_MEDIA_UPLOADED", "MEDIA_ASSET", assetId, "{}", "{}", null, requestId);
            return new Uploaded(assetId, "/api/v1/media/" + assetId + "/preview", image.mediaType(), image.width(), image.height(), altText.trim());
        } catch (RuntimeException error) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) try { storage.delete(key); } catch (RuntimeException ignored) { }
            throw error;
        }
    }
}
