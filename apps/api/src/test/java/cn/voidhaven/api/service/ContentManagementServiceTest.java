package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.ContentRevision;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.Visibility;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.persistence.ContentRevisionRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContentManagementServiceTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test void persists_resource_type_in_its_typed_detail_table() throws Exception {
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentRevisionRepository revisions = mock(ContentRevisionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(items.saveAndFlush(any(ContentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ContentManagementService service = new ContentManagementService(
                items, revisions, mock(PartnerProfileRepository.class), mock(AuditService.class), json,
                new ContentSourcePolicy(false, "https://voidhaven.example"), new RichTextPolicy(), new ContentLanguagePolicy(), jdbc);

        service.create(new ContentManagementService.Draft(
                        "RESOURCE", "合成资源", "用于验证类型明细写入。",
                        json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"text\":\"正文\"}]}"),
                        "zh-CN", null, "https://resources.example.edu/guide", "合成来源", "已授权", "合成作者", "NOT_ALLOWED", "NOT_ALLOWED", json.readTree("{\"resourceType\":\"GUIDE\"}")),
                null, "01JADMIN000000000000000000", "ADMIN", "01JREQUEST0000000000000000");

        verify(jdbc).update(startsWith("insert into resource_revision_details"), anyString(), eq("GUIDE"));
    }

    @Test void rejects_non_chinese_content_without_an_original_title_before_it_can_enter_review() throws Exception {
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentRevisionRepository revisions = mock(ContentRevisionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ContentManagementService service = new ContentManagementService(
                items, revisions, mock(PartnerProfileRepository.class), mock(AuditService.class), json,
                new ContentSourcePolicy(false, "https://voidhaven.example"), new RichTextPolicy(), new ContentLanguagePolicy(), jdbc);

        assertThatThrownBy(() -> service.create(new ContentManagementService.Draft(
                        "RESOURCE", "English source", "中文导读", json.readTree("{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"text\":\"正文\"}]}"),
                        "en", null, "https://resources.example.edu/guide", "Source", "已获授权", "Source author", "NOT_ALLOWED", "NOT_ALLOWED", json.readTree("{\"resourceType\":\"GUIDE\"}")),
                null, "01JADMIN000000000000000000", "ADMIN", "01JREQUEST0000000000000000"))
                .hasFieldOrPropertyWithValue("code", "ORIGINAL_TITLE_REQUIRED");
    }

    @Test void refuses_to_review_a_stale_item_version() {
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentItem item = mock(ContentItem.class);
        when(items.findWithCurrentRevisionById("01JCONTENT0000000000000000")).thenReturn(Optional.of(item));
        when(item.getVersion()).thenReturn(9L);
        ContentManagementService service = service(items, mock(JdbcTemplate.class), mock(AuditService.class));

        assertThatThrownBy(() -> service.decide("01JCONTENT0000000000000000", "APPROVE", null, 8L,
                "01JADMIN000000000000000000", "ADMIN", "01JREQUEST0000000000000000"))
                .hasFieldOrPropertyWithValue("code", "CONTENT_VERSION_CONFLICT");
    }

    @Test void reopens_a_rejected_revision_for_authenticated_author_changes() {
        ContentItemRepository items = mock(ContentItemRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        ContentItem item = rejectedItem();
        when(items.findWithCurrentRevisionById(item.getId())).thenReturn(Optional.of(item));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyString())).thenReturn(List.of());
        ContentManagementService service = service(items, jdbc, audit);

        var reopened = service.reopen(item.getId(), "补充来源后重新送审", "01JADMIN000000000000000000", "SUPER_ADMIN", "01JREQUEST0000000000000000");

        assertThat(reopened.workflowStatus()).isEqualTo(WorkflowStatus.CHANGES_REQUESTED);
        verify(audit).recordReview(eq(item.getId()), anyString(), eq("01JADMIN000000000000000000"), eq("REOPENED"), eq("补充来源后重新送审"));
    }

    @Test void returns_approved_content_for_changes_and_records_previous_status() {
        ContentItemRepository items = mock(ContentItemRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        ContentItem item = rejectedItem();
        item.approve();
        when(items.findWithCurrentRevisionById(item.getId())).thenReturn(Optional.of(item));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyString())).thenReturn(List.of());
        service(items, jdbc, audit).decide(item.getId(), "CHANGES_REQUESTED", "补齐行动入口", item.getVersion(),
                "01JADMIN000000000000000000", "ADMIN", "01JREQUEST0000000000000000");
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.CHANGES_REQUESTED);
        verify(audit).record(anyString(), anyString(), eq("REVIEW_CHANGES_REQUESTED"), eq("CONTENT_ITEM"), eq(item.getId()),
                eq("{\"workflowStatus\":\"APPROVED\"}"), eq("{\"workflowStatus\":\"CHANGES_REQUESTED\"}"), eq("补齐行动入口"), anyString());
    }

    @Test void approved_content_cannot_be_reapproved_or_rejected_or_returned_without_reason() {
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentItem item = rejectedItem();
        item.approve();
        when(items.findWithCurrentRevisionById(item.getId())).thenReturn(Optional.of(item));
        var service = service(items, mock(JdbcTemplate.class), mock(AuditService.class));
        for (String decision : List.of("APPROVE", "REJECT")) {
            assertThatThrownBy(() -> service.decide(item.getId(), decision, "原因", item.getVersion(), "admin", "ADMIN", "request"))
                    .hasFieldOrPropertyWithValue("code", "CONTENT_NOT_IN_REVIEW");
        }
        assertThatThrownBy(() -> service.decide(item.getId(), "CHANGES_REQUESTED", " ", item.getVersion(), "admin", "ADMIN", "request"))
                .hasFieldOrPropertyWithValue("code", "REVIEW_REASON_REQUIRED");
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
    }

    @Test void recruitment_without_action_cannot_be_approved() {
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentItem item = mock(ContentItem.class);
        when(item.getVersion()).thenReturn(7L);
        when(item.getWorkflowStatus()).thenReturn(WorkflowStatus.PENDING_REVIEW);
        when(item.getKind()).thenReturn(ContentKind.RECRUITMENT);
        when(items.findWithCurrentRevisionById("content")).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service(items, mock(JdbcTemplate.class), mock(AuditService.class))
                .decide("content", "APPROVE", null, 7L, "admin", "ADMIN", "request"))
                .hasFieldOrPropertyWithValue("code", "RECRUITMENT_ACTION_REQUIRED");
        verify(item, never()).approve();
    }

    private ContentManagementService service(ContentItemRepository items, JdbcTemplate jdbc, AuditService audit) {
        return new ContentManagementService(items, mock(ContentRevisionRepository.class), mock(PartnerProfileRepository.class), audit, json,
                new ContentSourcePolicy(false, "https://voidhaven.example"), new RichTextPolicy(), new ContentLanguagePolicy(), jdbc);
    }

    @Test void recruitment_can_be_resubmitted_and_approved_after_adding_an_action() {
        ContentItemRepository items = mock(ContentItemRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        ContentItem item = rejectedItem(ContentKind.RECRUITMENT);
        item.requestChanges();
        when(items.findWithCurrentRevisionById(item.getId())).thenReturn(Optional.of(item));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyString())).thenReturn(List.of());
        var service = service(items, jdbc, audit);
        ContentActionService actions = mock(ContentActionService.class);
        service.setContentActions(actions);
        assertThatThrownBy(() -> service.submitAsAdmin(item.getId(), "admin", "ADMIN", "request"))
                .hasFieldOrPropertyWithValue("code", "RECRUITMENT_ACTION_REQUIRED");
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.CHANGES_REQUESTED);
        when(actions.hasEnabledAction(item.getCurrentRevision().getId())).thenReturn(true);
        service.submitAsAdmin(item.getId(), "admin", "ADMIN", "request");
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.PENDING_REVIEW);
        service.decide(item.getId(), "APPROVE", null, item.getVersion(), "admin", "ADMIN", "request");
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.APPROVED);
    }

    @Test void returning_an_approved_candidate_preserves_the_published_revision() {
        ContentItemRepository items = mock(ContentItemRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ContentItem item = rejectedItem();
        item.approve();
        item.publish();
        ContentRevision published = item.getPublishedRevision();
        ContentRevision candidate = mock(ContentRevision.class);
        when(candidate.getId()).thenReturn("01JCANDIDATE000000000000000");
        when(candidate.getTitle()).thenReturn("待修改候选稿");
        item.setCurrentRevision(candidate);
        item.beginCandidateRevision();
        item.submit();
        item.approve();
        when(items.findWithCurrentRevisionById(item.getId())).thenReturn(Optional.of(item));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyString())).thenReturn(List.of());
        service(items, jdbc, mock(AuditService.class)).decide(item.getId(), "CHANGES_REQUESTED", "补充说明", item.getVersion(), "admin", "ADMIN", "request");
        assertThat(item.getPublishedRevision()).isSameAs(published);
        assertThat(item.getCurrentRevision()).isSameAs(candidate).isNotSameAs(published);
        assertThat(item.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    private ContentItem rejectedItem() {
        return rejectedItem(ContentKind.RESOURCE);
    }

    private ContentItem rejectedItem(ContentKind kind) {
        ContentItem item = new ContentItem("01JCONTENT0000000000000000", kind, WorkflowStatus.DRAFT, Visibility.HIDDEN, RecordState.ACTIVE);
        ContentRevision revision = new ContentRevision("01JREVISION000000000000000", item, 1, "被拒绝稿件", "摘要", "{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"text\":\"正文\"}]}",
                "zh-CN", null, "https://resources.example.edu/source", "来源", "已授权", "合成作者", "NOT_ALLOWED", "NOT_ALLOWED", "{\"resourceType\":\"GUIDE\"}", "01JAUTHOR00000000000000000");
        item.setCurrentRevision(revision);
        org.springframework.test.util.ReflectionTestUtils.setField(item, "version", 7L);
        item.submit();
        item.reject();
        return item;
    }
}
