package cn.voidhaven.api.web;

import cn.voidhaven.api.service.PublicWriteRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class PublicWriteRateLimitInterceptor implements HandlerInterceptor {
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/public/submissions",
            "/api/v1/public/partner-applications",
            "/api/v1/public/community-gate/verify");
    private final PublicWriteRateLimiter limiter;

    public PublicWriteRateLimitInterceptor(PublicWriteRateLimiter limiter) { this.limiter = limiter; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("POST".equals(request.getMethod()) && PROTECTED_PATHS.contains(request.getRequestURI())) limiter.requireAvailable(networkSubject(request));
        return true;
    }

    private String networkSubject(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",", 2)[0].trim();
        return request.getRemoteAddr();
    }
}
