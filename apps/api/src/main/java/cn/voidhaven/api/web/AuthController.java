package cn.voidhaven.api.web;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager auth; private final cn.voidhaven.api.service.ActivationService activations;
    public AuthController(AuthenticationConfiguration configuration, cn.voidhaven.api.service.ActivationService activations) throws Exception { this.auth = configuration.getAuthenticationManager(); this.activations = activations; }
    record LoginRequest(@NotBlank String loginId, @NotBlank String password) { }
    record Identity(String loginId, List<String> roles) { }
    record ActivationRequest(@NotBlank @Size(max = 256) String token, @NotBlank @Size(min = 12, max = 128) String password) { }
    @PostMapping("/session")
    ApiResponse<Identity> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        try { Authentication authentication = auth.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.loginId(), body.password())); SecurityContextHolder.getContext().setAuthentication(authentication); request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext()); return new ApiResponse<>(identity(authentication), requestId(request)); }
        catch (AuthenticationException error) { throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "账号或密码不正确，或账号当前不可用。"); }
    }
    @DeleteMapping("/session") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) { HttpSession session = request.getSession(false); if (session != null) session.invalidate(); SecurityContextHolder.clearContext(); }
    @GetMapping("/me") ApiResponse<Identity> me(Authentication authentication, HttpServletRequest request) { return new ApiResponse<>(identity(authentication), requestId(request)); }
    @GetMapping("/csrf") ApiResponse<CsrfPayload> csrf(CsrfToken token, HttpServletRequest request) { return new ApiResponse<>(new CsrfPayload(token.getHeaderName(), token.getToken()), requestId(request)); }
    @PostMapping("/activation/consume")
    ApiResponse<Map<String, String>> activate(@Valid @RequestBody ActivationRequest body, HttpServletRequest request) { var account = activations.consume(body.token(), body.password()); return new ApiResponse<>(Map.of("loginId", account.getLoginId(), "message", "账号已激活，请使用新密码登录。"), requestId(request)); }
    record CsrfPayload(String headerName, String token) { }
    private Identity identity(Authentication auth) { return new Identity(auth.getName(), auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).map(role -> role.replaceFirst("^ROLE_", "")).toList()); }
    private String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
