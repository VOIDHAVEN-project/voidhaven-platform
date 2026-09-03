package cn.voidhaven.api.web;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.persistence.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountEligibilityFilterTest {
    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test void invalidates_an_existing_session_when_the_account_is_no_longer_active() throws Exception {
        AccountRepository accounts = mock(AccountRepository.class);
        Account account = new Account("01JACCOUNT00000000000000000", "partner-one", "hash");
        account.suspend();
        when(accounts.findByLoginId("partner-one")).thenReturn(Optional.of(account));
        var authentication = UsernamePasswordAuthenticationToken.authenticated("partner-one", "n/a", List.of(new SimpleGrantedAuthority("ROLE_PARTNER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/partner/dashboard");
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "01JREQUEST0000000000000000");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AccountEligibilityFilter(accounts).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("ACCOUNT_INACTIVE");
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
