package cn.voidhaven.api.web;

import cn.voidhaven.api.service.AuthenticationRateLimiter;
import cn.voidhaven.api.service.PasswordSelfServiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordSelfServiceController {
    private static final Logger log = LoggerFactory.getLogger(PasswordSelfServiceController.class);
    private static final String GENERIC_RESULT = "如果账号存在且已绑定恢复邮箱，系统会发送一次性密码重置凭据。";

    record RequestVerification(@NotBlank @Size(max = 128) String currentPassword,
                               @NotBlank @Email @Size(max = 254) String email) { }
    record VerifyEmail(@NotBlank @Size(max = 256) String token) { }
    record RemoveEmail(@NotBlank @Size(max = 128) String currentPassword) { }
    record RequestReset(@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{4,100}") String loginId) { }

    private final PasswordSelfServiceService recovery;
    private final AuthenticationRateLimiter rateLimiter;
    private final ClientNetworkSubject networkSubject;

    public PasswordSelfServiceController(PasswordSelfServiceService recovery,
            AuthenticationRateLimiter rateLimiter, ClientNetworkSubject networkSubject) {
        this.recovery = recovery;
        this.rateLimiter = rateLimiter;
        this.networkSubject = networkSubject;
    }

    @GetMapping("/recovery-email")
    ApiResponse<PasswordSelfServiceService.RecoveryEmailStatus> status(Authentication auth,
                                                                       HttpServletRequest request) {
        return new ApiResponse<>(recovery.status(auth.getName()), requestId(request));
    }

    @PostMapping("/recovery-email/request-verification")
    ApiResponse<PasswordSelfServiceService.VerificationIssued> requestVerification(
            @Valid @RequestBody RequestVerification body, Authentication auth, HttpServletRequest request) {
        return new ApiResponse<>(recovery.requestVerification(auth.getName(), body.currentPassword(), body.email(),
                requestId(request)), requestId(request));
    }

    @PostMapping("/recovery-email/verify")
    ApiResponse<PasswordSelfServiceService.RecoveryEmailStatus> verify(
            @Valid @RequestBody VerifyEmail body, Authentication auth, HttpServletRequest request) {
        return new ApiResponse<>(recovery.verify(auth.getName(), body.token(), requestId(request)), requestId(request));
    }

    @PostMapping("/recovery-email/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@Valid @RequestBody RemoveEmail body, Authentication auth, HttpServletRequest request) {
        recovery.remove(auth.getName(), body.currentPassword(), requestId(request));
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponse<Map<String, String>> requestReset(@Valid @RequestBody RequestReset body,
                                                  HttpServletRequest request) {
        rateLimiter.beginPasswordReset(body.loginId(), networkSubject.resolve(request));
        try {
            recovery.requestPasswordReset(body.loginId(), requestId(request));
        } catch (RuntimeException error) {
            log.warn("Self-service password reset delivery failed; requestId={}", requestId(request));
        }
        return new ApiResponse<>(Map.of("message", GENERIC_RESULT), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
