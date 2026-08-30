package cn.voidhaven.api.web;
import cn.voidhaven.api.support.Ids;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "voidhaven.request-id";
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String id = Ids.ulid(); request.setAttribute(ATTRIBUTE, id); response.setHeader("X-Request-Id", id); chain.doFilter(request, response);
    }
}
