package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.IdempotencyService;
import cn.voidhaven.api.service.ResourceConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceConfigAuthorizationTest {
    @Configuration @EnableMethodSecurity static class MethodSecurity { }
    @Test void partner_cannot_read_configuration_and_both_administrator_roles_can() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var service = mock(ResourceConfigService.class);
            context.registerBean(ResourceConfigService.class, () -> service);
            context.registerBean(IdempotencyService.class, () -> mock(IdempotencyService.class));
            context.registerBean(AccountRepository.class, () -> mock(AccountRepository.class));
            context.register(MethodSecurity.class, ResourceConfigController.class); context.refresh();
            var controller = context.getBean(ResourceConfigController.class);
            var request = new MockHttpServletRequest();
            login("PARTNER");
            assertThatThrownBy(() -> controller.current(request)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(service);
            login("ADMIN"); controller.current(request);
            login("SUPER_ADMIN"); controller.current(request);
            verify(service, times(2)).current();
        } finally { SecurityContextHolder.clearContext(); }
    }
    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("isolated", "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
