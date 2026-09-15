package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.ContentRevision;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.Visibility;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaServiceTest {
    @Test void attaches_a_normalized_image_only_to_an_editable_draft_and_audits_the_minimum_event() {
        ContentItem item = new ContentItem("01JCONTENT0000000000000000", ContentKind.COMPETITION, WorkflowStatus.DRAFT, Visibility.HIDDEN, RecordState.ACTIVE);
        ContentRevision revision = new ContentRevision("01JREVISION000000000000000", item, 1, "草稿", "导读", "{}", "zh-CN", null, "https://contest.example.edu", "来源", "已授权", "测试作者", "NOT_ALLOWED", "NOT_ALLOWED", "{}", "01JADMIN000000000000000000");
        item.setCurrentRevision(revision);
        ContentItemRepository items = mock(ContentItemRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MediaStorage storage = mock(MediaStorage.class);
        MediaValidator validator = mock(MediaValidator.class);
        AuditService audit = mock(AuditService.class);
        when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        when(jdbc.queryForObject(startsWith("select count"), eq(Integer.class), eq(revision.getId()))).thenReturn(0);
        when(jdbc.queryForObject(startsWith("select coalesce"), eq(Long.class), eq(revision.getId()))).thenReturn(0L);
        when(validator.validate(any())).thenReturn(new MediaValidator.ValidatedImage(new byte[] {1, 2, 3}, "image/jpeg", 2, 3, "jpg"));
        MediaService service = new MediaService(items, mock(PartnerProfileRepository.class), jdbc, storage, validator, audit);

        MediaService.Uploaded uploaded = service.upload(item.getId(), "合成图片说明", new byte[] {9}, "01JADMIN000000000000000000", "ADMIN", true, "01JREQUEST0000000000000000");

        assertThat(uploaded.mediaType()).isEqualTo("image/jpeg");
        assertThat(uploaded.altText()).isEqualTo("合成图片说明");
        verify(storage).put(startsWith("content/" + revision.getId() + "/"), eq("image/jpeg"), aryEq(new byte[] {1, 2, 3}));
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
        verify(audit).record(eq("01JADMIN000000000000000000"), eq("ADMIN"), eq("MEDIA_UPLOADED"), eq("MEDIA_ASSET"), anyString(), eq("{}"), contains(revision.getId()), isNull(), eq("01JREQUEST0000000000000000"));
    }

    @Test void action_qr_upload_uses_a_reservation_instead_of_revision_media() {
        ContentItem item = new ContentItem("01JCONTENT0000000000000001", ContentKind.RECRUITMENT,
                WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE);
        ContentRevision revision = new ContentRevision("01JREVISION000000000000001", item, 1, "招募", "导读", "{}",
                "zh-CN", null, "https://example.org", "来源", "已授权", "测试作者",
                "NOT_ALLOWED", "NOT_ALLOWED", "{}", "01JADMIN000000000000000000");
        item.setCurrentRevision(revision);
        ContentItemRepository items = mock(ContentItemRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MediaStorage storage = mock(MediaStorage.class);
        MediaValidator validator = mock(MediaValidator.class);
        when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        when(jdbc.queryForObject(startsWith("select count"), eq(Integer.class), eq(item.getId()))).thenReturn(0);
        when(jdbc.queryForObject(startsWith("select coalesce"), eq(Long.class), eq(item.getId()))).thenReturn(0L);
        when(validator.validate(any())).thenReturn(
                new MediaValidator.ValidatedImage(new byte[] {1, 2, 3}, "image/png", 240, 240, "png"));
        MediaService service = new MediaService(items, mock(PartnerProfileRepository.class), jdbc, storage,
                validator, mock(AuditService.class));

        MediaService.Uploaded uploaded = service.uploadAction(item.getId(), "招募二维码", new byte[] {9},
                "01JADMIN000000000000000000", "ADMIN", true, "request-2");

        assertThat(uploaded.url()).contains("/api/v1/media/").endsWith("/preview");
        verify(storage).put(startsWith("content-action/" + item.getId() + "/"), eq("image/png"),
                aryEq(new byte[] {1, 2, 3}));
        verify(jdbc).update(startsWith("insert into content_action_media_reservations"), any(Object[].class));
        verify(jdbc, never()).update(contains("revision_media"), any(Object[].class));
    }

    @Test void action_media_cannot_be_reused_by_another_content_or_account() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(startsWith("select count(*) from media_assets ma"), eq(Integer.class),
                eq("01JMEDIA00000000000000002"), eq("01JREVISION000000000000002"),
                eq("01JCONTENT0000000000000002"), eq(false), eq("01JPARTNER0000000000000001")))
                .thenReturn(0);
        MediaService service = new MediaService(mock(ContentItemRepository.class),
                mock(PartnerProfileRepository.class), jdbc, mock(MediaStorage.class),
                mock(MediaValidator.class), mock(AuditService.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.requireActionMedia(
                "01JCONTENT0000000000000002", "01JREVISION000000000000002",
                "01JMEDIA00000000000000002", "01JPARTNER0000000000000001", false))
                .isInstanceOf(cn.voidhaven.api.web.ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_ACTION_MEDIA_INVALID");
    }
}
