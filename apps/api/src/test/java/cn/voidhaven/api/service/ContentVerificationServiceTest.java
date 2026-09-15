package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.ContentItemRepository;
import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentVerificationServiceTest {
    @Test void next_check_must_be_later_than_the_new_verification() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ContentItemRepository items = mock(ContentItemRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        when(items.existsById(anyString())).thenReturn(true);
        Account actor = new Account("01JADMIN000000000000000000", "admin", "hash", "ACTIVE");
        actor.addRole(new Role("ADMIN"));
        when(accounts.findByLoginId("admin")).thenReturn(Optional.of(actor));
        ContentVerificationService service = new ContentVerificationService(
                jdbc, items, accounts, mock(AuditService.class));

        assertThatThrownBy(() -> service.record("01JCONTENT0000000000000000", "VALID", null,
                Instant.now().minusSeconds(1), "admin", "request-1"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "CONTENT_VERIFICATION_NEXT_CHECK_INVALID");
        verify(jdbc, never()).update(startsWith("insert into content_verification_records"), any(Object[].class));

        Instant future = Instant.now().plusSeconds(3600);
        ContentVerificationService.Verification saved = service.record(
                "01JCONTENT0000000000000000", "VALID", null, future, "admin", "request-2");
        assertThat(saved.nextCheckAt()).isEqualTo(future);
        verify(jdbc).update(startsWith("insert into content_verification_records"), any(Object[].class));
    }
}
