package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.persistence.ContentRevisionRepository;
import cn.voidhaven.api.persistence.PartnerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
