package cn.voidhaven.api.config;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.security.AccountPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import cn.voidhaven.api.web.AccountEligibilityFilter;

@Configuration @EnableWebSecurity @EnableMethodSecurity
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
public class SecurityConfig {
    @Value("${voidhaven.cookie-secure:false}") boolean cookieSecure;
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<AccountEligibilityFilter> eligibilityRegistration(AccountEligibilityFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false); return registration;
    }
    @Bean UserDetailsService userDetailsService(AccountRepository accounts, cn.voidhaven.api.service.OrganizationAccessService organizations) { return login -> accounts.findByLoginId(login).filter(account -> account.isActive()).filter(account -> !account.hasRole("PARTNER") || organizations.profile(account.getId()).filter(p -> p.isActive()).isPresent()).map(account -> new AccountPrincipal(account, account.hasRole("PARTNER") ? organizations.require(account.getId()) : null)).orElseThrow(() -> new UsernameNotFoundException("Account not found")); }
    @Bean DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder encoder) { DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users); provider.setPasswordEncoder(encoder); return provider; }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider provider, AccountEligibilityFilter accountEligibilityFilter) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse(); csrf.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(cookieSecure));
        http.authenticationProvider(provider).csrf(config -> config.csrfTokenRepository(csrf).ignoringRequestMatchers("/api/v1/public/**", "/api/v1/auth/session", "/api/v1/auth/activation/consume", "/api/v1/auth/password-reset/consume"))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/healthz", "/actuator/health/**", "/api/v1/public/**", "/api/v1/auth/session", "/api/v1/auth/csrf", "/api/v1/auth/activation/consume", "/api/v1/auth/password-reset/consume").permitAll().requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN").requestMatchers("/api/v1/partner/**", "/api/v1/media/**").hasAnyRole("PARTNER", "ADMIN", "SUPER_ADMIN").anyRequest().authenticated())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)).exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))).httpBasic(httpBasic -> httpBasic.disable())
            .addFilterAfter(accountEligibilityFilter, SecurityContextHolderFilter.class);
        return http.build();
    }
}
