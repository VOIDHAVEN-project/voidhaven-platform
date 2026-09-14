package cn.voidhaven.api.web;

import cn.voidhaven.api.service.PublicStatusQueryRateLimiter;
import cn.voidhaven.api.service.PublicWriteRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class PublicWriteRateLimitInterceptor implements HandlerInterceptor {
    private static final Set<String> WRITE_PATHS = Set.of(
            "/api/v1/public/submissions",
            "/api/v1/public/partner-applications");
    private static final Set<String> STATUS_QUERY_PATHS = Set.of(
            "/api/v1/public/submissions/status-query",
            "/api/v1/public/partner-applications/status-query");
    private final PublicWriteRateLimiter writeLimiter;
    private final PublicStatusQueryRateLimiter statusQueryLimiter;
    private final ClientNetworkSubject networkSubject;

    public PublicWriteRateLimitInterceptor(PublicWriteRateLimiter writeLimiter,
                                           PublicStatusQueryRateLimiter statusQueryLimiter,
                                           ClientNetworkSubject networkSubject) {
        this.writeLimiter = writeLimiter;
        this.statusQueryLimiter = statusQueryLimiter;
        this.networkSubject = networkSubject;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) return true;
        String path = request.getRequestURI();
        if (WRITE_PATHS.contains(path)) writeLimiter.requireAvailable(networkSubject.resolve(request));
        else if (STATUS_QUERY_PATHS.contains(path)) statusQueryLimiter.requireAvailable(networkSubject.resolve(request));
        return true;
    }
}
