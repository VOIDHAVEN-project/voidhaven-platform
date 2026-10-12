package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectJoinServiceTest {
    @Test void public_listing_without_a_grant_only_loads_public_entries() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        CommunityService community = mock(CommunityService.class);
        ProjectJoinService service = service(jdbc, community, mock(PrivateValueCipher.class));

        service.publicEntries(null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("from project_join_entries");
        assertThat(sql.getValue()).contains("access_level = 'PUBLIC'");
        verify(community, never()).requireGrant(anyString());
    }

    @Test void public_listing_with_a_grant_reads_controlled_entries_through_the_shared_invite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        CommunityService community = mock(CommunityService.class);
        ProjectJoinService service = service(jdbc, community, mock(PrivateValueCipher.class));

        service.publicEntries("shared-grant");

        verify(community).requireGrant("shared-grant");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).doesNotContain("access_level = 'PUBLIC'");
    }

    @Test void update_rejects_stale_versions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForMap(contains("for update"), eq("01JPROJECT0000000000000001")))
                .thenReturn(Map.of("id", "01JPROJECT0000000000000001", "version", 3L, "active", true));
        ProjectJoinService service = service(jdbc, mock(CommunityService.class), mock(PrivateValueCipher.class));

        assertThatThrownBy(() -> service.updateEntry("01JPROJECT0000000000000001", 2, "项目组", "",
                "TEXT", "OTHER", "COMMUNITY_GATED", "hello", null, false, 0,
                "01JADMIN000000000000000000", "SUPER_ADMIN", "r"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "PROJECT_JOIN_ENTRY_VERSION_CONFLICT");
    }

    @Test void public_personal_contact_requires_an_explicit_risk_confirmation() {
        ProjectJoinService service = service(mock(JdbcTemplate.class), mock(CommunityService.class),
                mock(PrivateValueCipher.class));

        assertThatThrownBy(() -> service.createEntry("公开邮箱", null, "TEXT", "EMAIL", "PUBLIC",
                "person@example.org", null, false, 0,
                "01JADMIN000000000000000000", "SUPER_ADMIN", "r"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "PROJECT_JOIN_ENTRY_PUBLIC_EXPOSURE_CONFIRMATION_REQUIRED");
    }

    @Test void controlled_values_are_encrypted_with_the_project_join_context() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PrivateValueCipher privateValues = mock(PrivateValueCipher.class);
        when(privateValues.encrypt(eq("hello"), anyString()))
                .thenReturn(new PrivateValueCipher.EncryptedValue("v1:sealed", "k1"));
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(new ProjectJoinService.EntrySummary("01JPROJECT0000000000000001", "项目组", null,
                        "TEXT", "WECHAT", "COMMUNITY_GATED", null, true, 0, 0));
        ProjectJoinService service = service(jdbc, mock(CommunityService.class), privateValues);

        service.createEntry("项目组", null, "TEXT", "WECHAT", "COMMUNITY_GATED", "hello", null, false, 0,
                "01JADMIN000000000000000000", "SUPER_ADMIN", "r");

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(privateValues).encrypt(eq("hello"), context.capture());
        assertThat(context.getValue()).startsWith("PROJECT_JOIN_ENTRY:");
        verify(privateValues).requireKeys();
    }

    private ProjectJoinService service(JdbcTemplate jdbc, CommunityService community, PrivateValueCipher privateValues) {
        return new ProjectJoinService(jdbc, privateValues, mock(ContentSourcePolicy.class), mock(AuditService.class),
                community);
    }
}
