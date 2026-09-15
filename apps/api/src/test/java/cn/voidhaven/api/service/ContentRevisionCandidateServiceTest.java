package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.domain.ContentRevision;
import cn.voidhaven.api.domain.RecordState;
import cn.voidhaven.api.domain.Visibility;
import cn.voidhaven.api.domain.WorkflowStatus;
import cn.voidhaven.api.persistence.ContentRevisionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentRevisionCandidateServiceTest {
    @Test void clone_copies_revision_projections_and_keeps_the_published_revision() {
        ContentRevisionRepository revisions = mock(ContentRevisionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ContentItem item = new ContentItem("01JCONTENT0000000000000000", ContentKind.RECRUITMENT,
                WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE);
        ContentRevision published = new ContentRevision("01JPUBLISHED00000000000000", item, 3, "招募标题",
                "导读", "{\"version\":2,\"blocks\":[]}", "zh-CN", "原稿标题", "https://example.org/source",
                "来源", "已授权", "作者", "NOT_ALLOWED", "NOT_ALLOWED", "{\"deadlineDate\":\"2030-01-01\"}",
                "01JADMIN000000000000000000");
        published.setEvolvableFields("# 正文\n", 2, 4, "{\"schema\":true}", "{\"meta\":true}");
        item.setCurrentRevision(published);
        item.setPublishedRevision(published);
        when(revisions.saveAndFlush(any(ContentRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbc.queryForObject(startsWith("select coalesce(max(revision_number)"), eq(Integer.class), eq(item.getId())))
                .thenReturn(7);
        Map<String, Object> gated = new HashMap<>();
        gated.put("presentation_type", "TEXT");
        gated.put("contact_channel", "WECHAT");
        gated.put("access_level", "COMMUNITY_GATED");
        gated.put("label", "微信");
        gated.put("public_value", null);
        gated.put("private_value_ciphertext", "v1:cipher");
        gated.put("private_value_key_id", "key-1");
        gated.put("media_asset_id", null);
        gated.put("enabled", true);
        gated.put("sort_order", 0);
        when(jdbc.queryForList(startsWith("select presentation_type"), eq(published.getId()))).thenReturn(List.of(gated));

        ContentRevision candidate = new ContentRevisionCandidateService(revisions, jdbc)
                .cloneCurrent(item, "01JEDITOR00000000000000000");

        assertThat(candidate.getRevisionNumber()).isEqualTo(8);
        assertThat(candidate.getMarkdownSource()).isEqualTo("# 正文\n");
        assertThat(candidate.getBodyJson()).isEqualTo("{\"version\":2,\"blocks\":[]}");
        assertThat(candidate.getDetailsJson()).isEqualTo("{\"deadlineDate\":\"2030-01-01\"}");
        assertThat(candidate.getSchemaSnapshotJson()).isEqualTo("{\"schema\":true}");
        assertThat(candidate.getMetadataJson()).isEqualTo("{\"meta\":true}");
        assertThat(item.getPublishedRevision()).isSameAs(published);
        assertThat(item.getCurrentRevision()).isSameAs(candidate);
        assertThat(item.getWorkflowStatus()).isEqualTo(WorkflowStatus.DRAFT);
        verify(jdbc).update(startsWith("insert into competition_revision_details"), eq(candidate.getId()), eq(published.getId()));
        verify(jdbc).update(startsWith("insert into recruitment_revision_details"), eq(candidate.getId()), eq(published.getId()));
        verify(jdbc).update(startsWith("insert into resource_revision_details"), eq(candidate.getId()), eq(published.getId()));
        verify(jdbc).update(startsWith("insert into revision_filter_values"), eq(candidate.getId()), eq(published.getId()));
        verify(jdbc).update(startsWith("insert into revision_content_relations"), eq(candidate.getId()), eq(published.getId()));
        verify(jdbc).update(startsWith("insert into revision_media"), eq(candidate.getId()), eq(published.getId()));
        verify(jdbc).update(startsWith("insert into content_action_entries"), anyString(), eq(candidate.getId()),
                eq("TEXT"), eq("WECHAT"), eq("COMMUNITY_GATED"), eq("微信"), isNull(), eq("v1:cipher"), eq("key-1"),
                isNull(), eq(true), eq(0));
    }

    @Test void clone_uses_the_highest_existing_revision_number() {
        ContentRevisionRepository revisions = mock(ContentRevisionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ContentItem item = new ContentItem("01JCONTENT0000000000000001", ContentKind.RECRUITMENT,
                WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE);
        ContentRevision published = new ContentRevision("01JPUBLISHED00000000000001", item, 2, "招募",
                "导读", "{}", "zh-CN", null, "https://example.org", "来源", "已授权", "作者",
                "NOT_ALLOWED", "NOT_ALLOWED", "{}", "01JADMIN000000000000000000");
        item.setCurrentRevision(published);
        item.setPublishedRevision(published);
        when(revisions.saveAndFlush(any(ContentRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbc.queryForObject(startsWith("select coalesce(max(revision_number)"), eq(Integer.class), eq(item.getId())))
                .thenReturn(5);
        when(jdbc.queryForList(anyString(), eq(published.getId()))).thenReturn(List.of());

        ContentRevision candidate = new ContentRevisionCandidateService(revisions, jdbc)
                .cloneCurrent(item, "01JEDITOR00000000000000000");

        ArgumentCaptor<ContentRevision> saved = ArgumentCaptor.forClass(ContentRevision.class);
        verify(revisions).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRevisionNumber()).isEqualTo(6);
        assertThat(candidate.getRevisionNumber()).isEqualTo(6);
        assertThat(item.getPublishedRevision().getRevisionNumber()).isEqualTo(2);
    }
}
