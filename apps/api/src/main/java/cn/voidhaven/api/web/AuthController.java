package cn.voidhaven.api.web;

import cn.voidhaven.api.service.ActivationService;
import cn.voidhaven.api.service.AuthenticationRateLimiter;
import cn.voidhaven.api.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final ActivationService activations;
    private final AuthenticationRateLimiter rateLimiter;
    private final ClientNetworkSubject networkSubject;
    private final PasswordService passwords;
    private cn.voidhaven.api.service.OrganizationAccessService organizations;
    @org.springframework.beans.factory.annotation.Autowired
    void organizations(cn.voidhaven.api.service.OrganizationAccessService organizations) { this.organizations = organizations; }
    private cn.voidhaven.api.service.AdminAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    void authorization(cn.voidhaven.api.service.AdminAuthorizationService authorization) { this.authorization = authorization; }
    private cn.voidhaven.api.persistence.AccountRepository accounts;
    @org.springframework.beans.factory.annotation.Autowired
    void accounts(cn.voidhaven.api.persistence.AccountRepository accounts) { this.accounts = accounts; }

    public AuthController(AuthenticationConfiguration configuration, ActivationService activations,
                          AuthenticationRateLimiter rateLimiter, ClientNetworkSubject networkSubject,
                          PasswordService passwords) throws Exception {
        this.authenticationManager = configuration.getAuthenticationManager();
        this.activations = activations;
        this.rateLimiter = rateLimiter;
        this.networkSubject = networkSubject;
        this.passwords = passwords;
    }

    record LoginRequest(@NotBlank @Size(max = 100) String loginId, @NotBlank @Size(max = 128) String password) { }
    record Identity(String loginId, List<String> roles, String accountId, List<cn.voidhaven.api.service.AdminAuthorizationService.Grant> permissions, java.time.Instant authorizationExpiresAt, cn.voidhaven.api.service.OrganizationAccessService.Identity organization) { }
    record ActivationRequest(@NotBlank @Size(max = 256) String token, @NotBlank @Size(min = 12, max = 128) String password) { }
    record CsrfPayload(String headerName, String token) { }
    record PasswordChangeRequest(@NotBlank @Size(max = 128) String currentPassword,
                                 @NotBlank @Size(min = 12, max = 128) String newPassword) { }
    record PasswordResetRequest(@NotBlank @Size(max = 256) String token,
                                @NotBlank @Size(min = 12, max = 128) String newPassword) { }

    @PostMapping("/session")
    ApiResponse<Identity> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        String attemptId = rateLimiter.beginLogin(body.loginId(), networkSubject.resolve(request));
        HttpSession session = request.getSession(true);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.loginId(), body.password()));
            if (!(authentication.getPrincipal() instanceof cn.voidhaven.api.security.AccountPrincipal principal)
                    || accounts.findById(principal.accountId()).filter(principal::matches).filter(account -> organizations.matches(principal, account)).isEmpty())
                throw new BadCredentialsException("Account changed while authenticating");
            request.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            session = request.getSession(false);
            if (session == null) throw new IllegalStateException("Authenticated session is unavailable");
            session.setAttribute("SPRING_SECURITY_CONTEXT", context);
            rateLimiter.loginSucceeded(attemptId);
            return new ApiResponse<>(identity(authentication), requestId(request));
        } catch (AuthenticationException error) {
            rateLimiter.loginFailed(attemptId);
            SecurityContextHolder.clearContext();
            session.invalidate();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "账号或密码不正确，或账号当前不可用。");
        }
    }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    ApiResponse<Identity> me(Authentication authentication, HttpServletRequest request) {
        return new ApiResponse<>(identity(authentication), requestId(request));
    }

    @GetMapping("/csrf")
    ApiResponse<CsrfPayload> csrf(CsrfToken token, HttpServletRequest request) {
        return new ApiResponse<>(new CsrfPayload(token.getHeaderName(), token.getToken()), requestId(request));
    }

    @PostMapping("/activation/consume")
    ApiResponse<Map<String, String>> activate(@Valid @RequestBody ActivationRequest body, HttpServletRequest request) {
        rateLimiter.beginActivation(networkSubject.resolve(request));
        var account = activations.consume(body.token(), body.password(), requestId(request));
        return new ApiResponse<>(Map.of("loginId", account.getLoginId(), "message", "账号已激活，请使用新密码登录。"), requestId(request));
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody PasswordChangeRequest body, Authentication authentication, HttpServletRequest request) {
        passwords.change(authentication.getName(), body.currentPassword(), body.newPassword(), requestId(request));
        SecurityContextHolder.clearContext();
    }

    @PostMapping("/password-reset/consume")
    ApiResponse<Map<String, String>> resetPassword(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        rateLimiter.beginActivation(networkSubject.resolve(request));
        String loginId = passwords.consumeReset(body.token(), body.newPassword(), requestId(request));
        return new ApiResponse<>(Map.of("loginId", loginId, "message", "密码已重置，请使用新密码登录。"), requestId(request));
    }

    private Identity identity(Authentication authentication) {
        return new Identity(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(role -> role.startsWith("ROLE_"))
                .map(role -> role.substring("ROLE_".length())).sorted().toList(),
                ((cn.voidhaven.api.security.AccountPrincipal) authentication.getPrincipal()).accountId(),
                authorization.effective(), authorization.current(((cn.voidhaven.api.security.AccountPrincipal) authentication.getPrincipal()).accountId()).expiresAt(),
                organizations.identity(((cn.voidhaven.api.security.AccountPrincipal) authentication.getPrincipal()).accountId()));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
