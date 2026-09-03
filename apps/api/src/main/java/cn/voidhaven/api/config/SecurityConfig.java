package cn.voidhaven.api.config;
import cn.voidhaven.api.persistence.AccountRepository;
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
public class SecurityConfig {
    @Value("${voidhaven.cookie-secure:false}") boolean cookieSecure;
    @Bean PasswordEncoder passwordEncoder() { return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }
    @Bean UserDetailsService userDetailsService(AccountRepository accounts) { return login -> accounts.findByLoginId(login).filter(account -> account.isActive()).map(account -> User.withUsername(account.getLoginId()).password(account.getPasswordHash()).authorities(account.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode())).toList()).build()).orElseThrow(() -> new UsernameNotFoundException("Account not found")); }
    @Bean DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder encoder) { DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users); provider.setPasswordEncoder(encoder); return provider; }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider provider, AccountEligibilityFilter accountEligibilityFilter) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse(); csrf.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(cookieSecure));
        http.authenticationProvider(provider).csrf(config -> config.csrfTokenRepository(csrf).ignoringRequestMatchers("/api/v1/public/**", "/api/v1/auth/session", "/api/v1/auth/activation/consume"))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/healthz", "/actuator/health/**", "/api/v1/public/**", "/api/v1/auth/session", "/api/v1/auth/csrf", "/api/v1/auth/activation/consume").permitAll().requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN").requestMatchers("/api/v1/partner/**", "/api/v1/media/**").hasAnyRole("PARTNER", "ADMIN", "SUPER_ADMIN").anyRequest().authenticated())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)).exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))).httpBasic(httpBasic -> httpBasic.disable())
            .addFilterAfter(accountEligibilityFilter, SecurityContextHolderFilter.class);
        return http.build();
    }
}
