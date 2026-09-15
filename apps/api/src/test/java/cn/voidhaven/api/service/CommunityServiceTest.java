package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityServiceTest {
    @Test void update_rejects_stale_versions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(contains("for update"), eq("01JENTRY00000000000000000001")))
                .thenReturn(Map.of("id", "01JENTRY00000000000000000001", "version", 3L, "active", true));
        CommunityService service = service(jdbc);

        assertThatThrownBy(() -> service.updateEntry("01JENTRY00000000000000000001", 2, "主群", "", "TEXT", "OTHER",
                "COMMUNITY_GATED", "hello", null, false, true, 0,
                "01JADMIN000000000000000000", "SUPER_ADMIN", "r"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_ENTRY_VERSION_CONFLICT");
    }

    @Test void deactivate_clears_primary_in_the_same_update() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(contains("for update"), eq("01JENTRY00000000000000000001")))
                .thenReturn(Map.of("id", "01JENTRY00000000000000000001", "version", 1L, "active", true));
        when(jdbc.update(contains("active = false"), eq("01JENTRY00000000000000000001"), eq(1L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("01JENTRY00000000000000000001")))
                .thenReturn(new CommunityService.EntrySummary("01JENTRY00000000000000000001", "主群", null,
                        "TEXT", "OTHER", "COMMUNITY_GATED", null, false, false, 0, 2));
        CommunityService service = service(jdbc);

        service.deactivateEntry("01JENTRY00000000000000000001", 1, "不再使用", "01JADMIN000000000000000000", "SUPER_ADMIN", "r");

        verify(jdbc).update(contains("is_primary = false"), eq("01JENTRY00000000000000000001"), eq(1L));
    }

    @Test void setting_primary_without_a_new_value_preserves_the_encrypted_entry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String id = "01JENTRY00000000000000000001";
        when(jdbc.queryForMap(contains("for update"), eq(id))).thenReturn(Map.of(
                "id", id,
                "version", 4L,
                "active", true,
                "presentation_type", "TEXT",
                "contact_channel", "WECHAT",
                "access_level", "COMMUNITY_GATED",
                "private_value_ciphertext", "v1:protected",
                "private_value_key_id", "k1"));
        when(jdbc.update(contains("set name ="), eq("主群"), isNull(), eq("TEXT"), eq("WECHAT"),
                eq("COMMUNITY_GATED"), isNull(), eq("v1:protected"), eq("k1"), isNull(), eq(true), eq(0), eq(id), eq(4L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(id)))
                .thenReturn(new CommunityService.EntrySummary(id, "主群", null, "TEXT", "WECHAT",
                        "COMMUNITY_GATED", null, true, true, 0, 5));

        CommunityService service = service(jdbc);
        service.updateEntry(id, 4, "主群", null, "TEXT", "WECHAT", "COMMUNITY_GATED", null, null,
                false, true, 0,
                "01JADMIN000000000000000000", "SUPER_ADMIN", "r");

        verify(jdbc).update(contains("set name ="), eq("主群"), isNull(), eq("TEXT"), eq("WECHAT"),
                eq("COMMUNITY_GATED"), isNull(), eq("v1:protected"), eq("k1"), isNull(), eq(true), eq(0), eq(id), eq(4L));
    }

    @Test void public_personal_contact_requires_an_explicit_risk_confirmation() {
        CommunityService service = service(mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.createEntry("公开邮箱", null, "TEXT", "EMAIL", "PUBLIC",
                "person@example.org", null, false, false, 0,
                "01JADMIN000000000000000000", "SUPER_ADMIN", "r"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_ENTRY_PUBLIC_EXPOSURE_CONFIRMATION_REQUIRED");
    }

    @Test void public_listing_without_a_grant_only_loads_public_entries() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(java.util.List.of());
        CommunityService service = service(jdbc);

        service.publicEntries(null);

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class));
        assertThat(sql.getValue()).contains("access_level = 'PUBLIC'");
    }

    private CommunityService service(JdbcTemplate jdbc) {
        return new CommunityService(jdbc, mock(PrivateValueCipher.class), mock(ContentSourcePolicy.class),
                mock(AuditService.class), true, 30, "test-community-secret", new MockEnvironment());
    }
}
