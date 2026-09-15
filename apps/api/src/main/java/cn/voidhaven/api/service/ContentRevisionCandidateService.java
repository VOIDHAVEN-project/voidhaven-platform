package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentRevision;
import cn.voidhaven.api.persistence.ContentRevisionRepository;
import cn.voidhaven.api.support.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ContentRevisionCandidateService {
    private final ContentRevisionRepository revisions;
    private final JdbcTemplate jdbc;

    public ContentRevisionCandidateService(ContentRevisionRepository revisions, JdbcTemplate jdbc) {
        this.revisions = revisions;
        this.jdbc = jdbc;
    }

    /** Creates an exact editable copy while leaving the published revision untouched. */
    public ContentRevision cloneCurrent(ContentItem item, String actorId) {
        ContentRevision source = item.getCurrentRevision();
        Integer maxNumber = jdbc.queryForObject(
                "select coalesce(max(revision_number), 0) from content_revisions where content_item_id = ?",
                Integer.class, item.getId());
        int nextNumber = Math.max(source.getRevisionNumber(), maxNumber == null ? 0 : maxNumber) + 1;
        ContentRevision target = new ContentRevision(Ids.ulid(), item, nextNumber,
                source.getTitle(), source.getSummary(), source.getBodyJson(), source.getLanguageCode(),
                source.getOriginalTitle(), source.getSourceUrl(), source.getSourceName(), source.getRightsBasis(),
                source.getAuthorDisplayName(), source.getReprintPermission(), source.getAdaptationPermission(),
                source.getDetailsJson(), actorId);
        target.setEvolvableFields(source.getMarkdownSource(), source.getDocumentVersion(), source.getSchemaVersion(),
                source.getSchemaSnapshotJson(), source.getMetadataJson());
        target = revisions.saveAndFlush(target);
        String from = source.getId();
        String to = target.getId();
        jdbc.update("insert into competition_revision_details (revision_id, deadline_at, deadline_date, official_rules_url) select ?, deadline_at, deadline_date, official_rules_url from competition_revision_details where revision_id = ?", to, from);
        jdbc.update("insert into recruitment_revision_details (revision_id, deadline_at, deadline_date, related_competition_id, follow_up_url) select ?, deadline_at, deadline_date, related_competition_id, follow_up_url from recruitment_revision_details where revision_id = ?", to, from);
        jdbc.update("insert into resource_revision_details (revision_id, resource_type) select ?, resource_type from resource_revision_details where revision_id = ?", to, from);
        jdbc.update("insert into revision_filter_values (revision_id, field_key, value_type, value_text, value_number, value_date) select ?, field_key, value_type, value_text, value_number, value_date from revision_filter_values where revision_id = ?", to, from);
        jdbc.update("insert into revision_content_relations (revision_id, target_content_item_id, relation_key) select ?, target_content_item_id, relation_key from revision_content_relations where revision_id = ?", to, from);
        jdbc.update("insert into revision_media (revision_id, media_asset_id, sort_order, alt_text) select ?, media_asset_id, sort_order, alt_text from revision_media where revision_id = ?", to, from);
        for (Map<String, Object> action : jdbc.queryForList("select presentation_type, contact_channel, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order from content_action_entries where revision_id = ?", from)) {
            jdbc.update("insert into content_action_entries (id, revision_id, presentation_type, contact_channel, access_level, label, public_value, private_value_ciphertext, private_value_key_id, media_asset_id, enabled, sort_order) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Ids.ulid(), to, action.get("presentation_type"), action.get("contact_channel"), action.get("access_level"),
                    action.get("label"), action.get("public_value"), action.get("private_value_ciphertext"),
                    action.get("private_value_key_id"), action.get("media_asset_id"), action.get("enabled"), action.get("sort_order"));
        }
        item.setCurrentRevision(target);
        item.beginCandidateRevision();
        return target;
    }
}
