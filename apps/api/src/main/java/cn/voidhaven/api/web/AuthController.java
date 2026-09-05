package cn.voidhaven.api.web;

import cn.voidhaven.api.service.ActivationService;
import cn.voidhaven.api.service.AuthenticationRateLimiter;
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
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final ActivationService activations;
    private final AuthenticationRateLimiter rateLimiter;
    private final ClientNetworkSubject networkSubject;

    public AuthController(AuthenticationConfiguration configuration, ActivationService activations,
                          AuthenticationRateLimiter rateLimiter, ClientNetworkSubject networkSubject) throws Exception {
        this.authenticationManager = configuration.getAuthenticationManager();
        this.activations = activations;
        this.rateLimiter = rateLimiter;
        this.networkSubject = networkSubject;
    }

    record LoginRequest(@NotBlank @Size(max = 100) String loginId, @NotBlank @Size(max = 128) String password) { }
    record Identity(String loginId, List<String> roles) { }
    record ActivationRequest(@NotBlank @Size(max = 256) String token, @NotBlank @Size(min = 12, max = 128) String password) { }
    record CsrfPayload(String headerName, String token) { }

    @PostMapping("/session")
    ApiResponse<Identity> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        rateLimiter.beginLogin(body.loginId(), networkSubject.resolve(request));
        HttpSession session = request.getSession(true);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.loginId(), body.password()));
            request.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            session = request.getSession(false);
            if (session == null) throw new IllegalStateException("Authenticated session is unavailable");
            session.setAttribute("SPRING_SECURITY_CONTEXT", context);
            rateLimiter.loginSucceeded(body.loginId());
            return new ApiResponse<>(identity(authentication), requestId(request));
        } catch (AuthenticationException error) {
            rateLimiter.loginFailed(body.loginId());
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
        var account = activations.consume(body.token(), body.password());
        return new ApiResponse<>(Map.of("loginId", account.getLoginId(), "message", "账号已激活，请使用新密码登录。"), requestId(request));
    }

    private Identity identity(Authentication authentication) {
        return new Identity(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(role -> role.startsWith("ROLE_"))
                .map(role -> role.substring("ROLE_".length())).sorted().toList());
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
