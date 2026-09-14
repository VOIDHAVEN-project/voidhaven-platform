package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.ContentItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContentLifecycleServiceTest {
    @Test void closes_a_competition_from_its_typed_deadline_not_the_json_projection() {
        ContentItem item = new ContentItem("01JCONTENT0000000000000000", ContentKind.COMPETITION, WorkflowStatus.APPROVED, Visibility.HIDDEN, RecordState.ACTIVE);
        ContentRevision revision = new ContentRevision("01JREVISION000000000000000", item, 1, "测试竞赛", "测试", "{}", "zh-CN", null, "https://contest.example.edu/rules", "来源", "已授权", "测试作者", "NOT_ALLOWED", "NOT_ALLOWED", "{}", null);
        item.setCurrentRevision(revision);
        item.publish();
        ContentItemRepository items = mock(ContentItemRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AuditService audit = mock(AuditService.class);
        when(items.findTimedPublicItems()).thenReturn(List.of(item));
        when(items.findByIdForUpdate(item.getId())).thenReturn(java.util.Optional.of(item));
        Map<String, Object> detail = new HashMap<>();
        detail.put("deadline_at", null);
        detail.put("deadline_date", Date.valueOf("2026-09-02"));
        when(jdbc.queryForList(startsWith("select deadline_at, deadline_date from competition_revision_details"), eq(revision.getId())))
                .thenReturn(List.of(detail));

        new ContentLifecycleService(items, jdbc, audit).run(Instant.parse("2026-09-03T00:00:00Z"));

        assertThat(item.getPublicationStatus()).isEqualTo(PublicationStatus.CLOSED);
        verify(audit).record(isNull(), eq("SYSTEM"), eq("CONTENT_CLOSED"), eq("CONTENT_ITEM"), eq(item.getId()), anyString(), anyString(), eq("截止日期已到"), anyString());
    }
}
