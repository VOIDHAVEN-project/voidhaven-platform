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
}
