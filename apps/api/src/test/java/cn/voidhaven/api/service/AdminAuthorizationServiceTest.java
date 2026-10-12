package cn.voidhaven.api.service;
import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.security.AccountPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAuthorizationServiceTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private AdminAuthorizationService service() {
        return spy(new AdminAuthorizationService(mock(JdbcTemplate.class), mock(AccountRepository.class), new JsonMapper(),
                mock(AuditService.class), mock(SecurityRevocationService.class), mock(ContentModuleRegistry.class)));
    }
    @Test void scopes_belong_to_each_permission_and_do_not_cross_combine() {
        var service = service(); doReturn(false).when(service).isSuper();
        doReturn(List.of(new AdminAuthorizationService.Grant("CONTENT_EDIT", "MODULES", List.of("competition")),
                new AdminAuthorizationService.Grant("CONTENT_PUBLISH", "ORGANIZATIONS", List.of("org-a")))).when(service).effective();
        assertThat(service.allows("CONTENT_EDIT", "competition", "org-b")).isTrue();
        assertThat(service.allows("CONTENT_EDIT", "resource", "org-a")).isFalse();
        assertThat(service.allows("CONTENT_PUBLISH", "competition", "org-b")).isFalse();
        assertThat(service.allows("CONTENT_READ", "resource", "org-a")).isTrue();
        assertThat(service.allows("PRIVATE_DATA_READ", "competition", "org-a")).isFalse();
    }
    @Test void expired_and_missing_authorizations_are_empty_without_a_scheduler() {
        var accounts = mock(AccountRepository.class); Account admin = new Account("a", "admin", "hash"); admin.addRole(new Role("ADMIN"));
        var principal = new AccountPrincipal(admin); SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(accounts.findById("a")).thenReturn(Optional.of(admin));
        var service = spy(new AdminAuthorizationService(mock(JdbcTemplate.class), accounts, new JsonMapper(), mock(AuditService.class), mock(SecurityRevocationService.class), mock(ContentModuleRegistry.class)));
        var grant = new AdminAuthorizationService.Grant("CONTENT_EDIT", "GLOBAL", List.of());
        doReturn(new AdminAuthorizationService.Authorization(List.of(grant), false, Instant.now().minusSeconds(1), 1)).when(service).current("a");
        assertThat(service.effective()).isEmpty();
        doReturn(new AdminAuthorizationService.Authorization(List.of(), false, null, 0)).when(service).current("a");
        assertThat(service.has("CONTENT_EDIT")).isFalse();
    }
    @Test void sql_scope_filters_preserve_permission_boundaries() {
        var service = service(); doReturn(false).when(service).isSuper();
        doReturn(List.of(new AdminAuthorizationService.Grant("CONTENT_REVIEW", "MODULES", List.of("resource")), new AdminAuthorizationService.Grant("CONTENT_EDIT", "GLOBAL", List.of()))).when(service).effective();
        var args = new ArrayList<Object>(); assertThat(service.contentPredicate("CONTENT_REVIEW", "c", args)).isEqualTo("(c.module_key in (?))");
        assertThat(args).containsExactly("resource");
        var readArgs = new ArrayList<Object>();
        assertThat(service.contentPredicate("CONTENT_READ", "c", readArgs)).isEqualTo("1=1");
        assertThat(readArgs).isEmpty();
    }
}
