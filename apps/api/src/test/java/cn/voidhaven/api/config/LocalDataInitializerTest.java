package cn.voidhaven.api.config;

import cn.voidhaven.api.domain.ContentItem;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LocalDataInitializerTest {
    @Test void assigns_module_key_before_the_first_content_insert() throws Exception {
        RoleRepository roles = mock(RoleRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        PartnerProfileRepository profiles = mock(PartnerProfileRepository.class);
        ContentItemRepository items = mock(ContentItemRepository.class);
        ContentRevisionRepository revisions = mock(ContentRevisionRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(roles.findByCode(anyString())).thenAnswer(call -> Optional.of(new Role(call.getArgument(0))));
        when(items.count()).thenReturn(0L);
        when(items.saveAndFlush(any(ContentItem.class))).thenAnswer(call -> {
            ContentItem item = call.getArgument(0);
            assertThat(item.getModuleKey()).isNotBlank();
            return item;
        });
        when(revisions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        LocalDataInitializer initializer = new LocalDataInitializer(roles, accounts, profiles, items, revisions, mock(PasswordEncoder.class), jdbc);
        ReflectionTestUtils.setField(initializer, "adminLogin", "");
        ReflectionTestUtils.setField(initializer, "adminPassword", "");
        ReflectionTestUtils.setField(initializer, "partnerLogin", "");
        ReflectionTestUtils.setField(initializer, "partnerPassword", "");
        ReflectionTestUtils.setField(initializer, "publicBaseUrl", "http://localhost:8080");

        initializer.run(null);

        verify(items, times(6)).saveAndFlush(any(ContentItem.class));
    }
}
