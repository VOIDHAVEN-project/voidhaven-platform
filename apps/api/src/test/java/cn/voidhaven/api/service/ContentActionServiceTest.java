package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.ContentRevision;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentActionServiceTest {
    @Test void replacing_actions_on_published_content_writes_only_to_a_new_candidate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentRevisionCandidateService candidates = mock(ContentRevisionCandidateService.class);
        ContentItem item = mock(ContentItem.class);
        ContentRevision published = mock(ContentRevision.class);
        ContentRevision candidate = mock(ContentRevision.class);
        when(item.getVersion()).thenReturn(4L, 5L);
        when(item.getKind()).thenReturn(ContentKind.RECRUITMENT);
        when(item.getRecordState()).thenReturn(RecordState.ACTIVE);
        when(item.getWorkflowStatus()).thenReturn(WorkflowStatus.PUBLISHED, WorkflowStatus.DRAFT);
        when(item.getCurrentRevision()).thenReturn(published);
        when(published.getId()).thenReturn("01JPUBLISHED000000000000000");
        when(candidate.getId()).thenReturn("01JCANDIDATE000000000000000");
        when(items.findByIdForUpdate("01JCONTENT0000000000000000")).thenReturn(Optional.of(item));
        when(items.saveAndFlush(item)).thenReturn(item);
        when(candidates.cloneCurrent(item, "01JADMIN000000000000000000")).thenReturn(candidate);
        when(jdbc.queryForObject(startsWith("select count"), eq(Integer.class), anyString())).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("01JCANDIDATE000000000000000"))).thenReturn(List.of());
        ContentActionService service = new ContentActionService(jdbc, items, candidates, mock(MediaService.class),
                mock(PrivateValueCipher.class), mock(ContentSourcePolicy.class), mock(CommunityService.class),
                mock(AuditService.class));

        ContentActionService.ActionSet result = service.replace("01JCONTENT0000000000000000", null, 4,
                List.of(new ContentActionService.ActionInput("LINK", "URL", "PUBLIC", "立即报名",
                        "https://example.org/apply", null, false, true, 0)),
                "01JADMIN000000000000000000", "ADMIN", "request-1");

        assertThat(result.candidateCreated()).isTrue();
        assertThat(result.workflowStatus()).isEqualTo("DRAFT");
        assertThat(result.version()).isEqualTo(5);
        verify(candidates).cloneCurrent(item, "01JADMIN000000000000000000");
        verify(jdbc).update("delete from content_action_entries where revision_id = ?", "01JCANDIDATE000000000000000");
        verify(jdbc, never()).update("delete from content_action_entries where revision_id = ?", "01JPUBLISHED000000000000000");
    }

    @Test void review_states_cannot_replace_actions() {
        ContentItem item = mock(ContentItem.class);
        when(item.getKind()).thenReturn(ContentKind.RECRUITMENT);
        when(item.getRecordState()).thenReturn(RecordState.ACTIVE);
        when(item.getWorkflowStatus()).thenReturn(WorkflowStatus.PENDING_REVIEW);
        when(item.getVersion()).thenReturn(2L);
        when(item.getCurrentRevision()).thenReturn(mock(ContentRevision.class));
        ContentItemRepository items = mock(ContentItemRepository.class);
        when(items.findByIdForUpdate("01JCONTENT0000000000000000")).thenReturn(Optional.of(item));
        ContentRevisionCandidateService candidates = mock(ContentRevisionCandidateService.class);
        ContentActionService service = new ContentActionService(mock(JdbcTemplate.class), items, candidates,
                mock(MediaService.class), mock(PrivateValueCipher.class), mock(ContentSourcePolicy.class),
                mock(CommunityService.class), mock(AuditService.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.replace("01JCONTENT0000000000000000", null, 2,
                List.of(), "01JADMIN000000000000000000", "ADMIN", "request-2"))
                .isInstanceOf(cn.voidhaven.api.web.ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_ACTIONS_NOT_EDITABLE");
        verify(candidates, never()).cloneCurrent(any(), anyString());
    }

    @Test void public_qr_without_confirmation_is_rejected_before_a_candidate_is_created() {
        ContentActionService service = draftService();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.replace("01JCONTENT0000000000000000", null, 2,
                List.of(new ContentActionService.ActionInput("QR_CODE", "WECHAT", "PUBLIC", "二维码",
                        null, "01JMEDIA00000000000000000", false, true, 0)),
                "01JADMIN000000000000000000", "ADMIN", "request-3"))
                .isInstanceOf(cn.voidhaven.api.web.ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_ACTION_PUBLIC_EXPOSURE_CONFIRMATION_REQUIRED");
    }

    @Test void link_and_email_cannot_be_combined() {
        ContentActionService service = draftService();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.replace("01JCONTENT0000000000000000", null, 2,
                List.of(new ContentActionService.ActionInput("LINK", "EMAIL", "COMMUNITY_GATED", "邮箱",
                        "person@example.org", null, false, true, 0)),
                "01JADMIN000000000000000000", "ADMIN", "request-4"))
                .isInstanceOf(cn.voidhaven.api.web.ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_ACTION_COMBINATION_INVALID");
    }

    @Test void gated_text_is_stored_as_ciphertext_and_omitted_from_audit() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentItem item = mock(ContentItem.class);
        ContentRevision draft = mock(ContentRevision.class);
        when(item.getVersion()).thenReturn(2L);
        when(item.getKind()).thenReturn(ContentKind.RECRUITMENT);
        when(item.getRecordState()).thenReturn(RecordState.ACTIVE);
        when(item.getWorkflowStatus()).thenReturn(WorkflowStatus.DRAFT);
        when(item.getCurrentRevision()).thenReturn(draft);
        when(draft.getId()).thenReturn("01JDRAFT0000000000000000000");
        when(items.findByIdForUpdate("01JCONTENT0000000000000000")).thenReturn(Optional.of(item));
        when(items.saveAndFlush(item)).thenReturn(item);
        when(jdbc.queryForObject(startsWith("select count"), eq(Integer.class), anyString())).thenReturn(0);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("01JDRAFT0000000000000000000"))).thenReturn(List.of());
        PrivateValueCipher cipher = mock(PrivateValueCipher.class);
        when(cipher.encrypt("person@example.org", "CONTENT_ACTION"))
                .thenReturn(new PrivateValueCipher.EncryptedValue("v1:cipher", "key-1"));
        AuditService audit = mock(AuditService.class);
        ContentActionService service = new ContentActionService(jdbc, items, mock(ContentRevisionCandidateService.class),
                mock(MediaService.class), cipher, mock(ContentSourcePolicy.class), mock(CommunityService.class), audit);

        service.replace("01JCONTENT0000000000000000", null, 2,
                List.of(new ContentActionService.ActionInput("TEXT", "EMAIL", "COMMUNITY_GATED", "联系邮箱",
                        "person@example.org", null, false, true, 0)),
                "01JADMIN000000000000000000", "ADMIN", "request-5");

        verify(jdbc).update(startsWith("insert into content_action_entries"), anyString(), eq("01JDRAFT0000000000000000000"),
                eq("TEXT"), eq("EMAIL"), eq("COMMUNITY_GATED"), eq("联系邮箱"), isNull(), eq("v1:cipher"), eq("key-1"),
                isNull(), eq(true), eq(0));
        verify(audit).record(eq("01JADMIN000000000000000000"), eq("ADMIN"), eq("CONTENT_ACTIONS_REPLACED"),
                eq("CONTENT_ITEM"), eq("01JCONTENT0000000000000000"), anyString(),
                org.mockito.ArgumentMatchers.argThat(after -> after != null && !after.contains("person@example.org")),
                isNull(), eq("request-5"));
    }

    private ContentActionService draftService() {
        ContentItem item = mock(ContentItem.class);
        when(item.getKind()).thenReturn(ContentKind.RECRUITMENT);
        when(item.getRecordState()).thenReturn(RecordState.ACTIVE);
        when(item.getWorkflowStatus()).thenReturn(WorkflowStatus.DRAFT);
        when(item.getVersion()).thenReturn(2L);
        when(item.getCurrentRevision()).thenReturn(mock(ContentRevision.class));
        ContentItemRepository items = mock(ContentItemRepository.class);
        when(items.findByIdForUpdate("01JCONTENT0000000000000000")).thenReturn(Optional.of(item));
        return new ContentActionService(mock(JdbcTemplate.class), items, mock(ContentRevisionCandidateService.class),
                mock(MediaService.class), mock(PrivateValueCipher.class), mock(ContentSourcePolicy.class),
                mock(CommunityService.class), mock(AuditService.class));
    }
}
