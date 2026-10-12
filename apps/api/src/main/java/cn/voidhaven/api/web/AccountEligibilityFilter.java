package cn.voidhaven.api.web;

import cn.voidhaven.api.persistence.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rechecks account eligibility for every authenticated request and invalidates stale sessions. */
@Component
public class AccountEligibilityFilter extends OncePerRequestFilter {
    private cn.voidhaven.api.service.OrganizationAccessService organizations;
    @org.springframework.beans.factory.annotation.Autowired
    void organizations(cn.voidhaven.api.service.OrganizationAccessService organizations) { this.organizations = organizations; }
    private final AccountRepository accounts;
    private cn.voidhaven.api.service.RequestSecurityService requests;
    @org.springframework.beans.factory.annotation.Autowired
    void requestSecurity(cn.voidhaven.api.service.RequestSecurityService requests) { this.requests = requests; }

    public AccountEligibilityFilter(AccountRepository accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)
                && (!(authentication.getPrincipal() instanceof cn.voidhaven.api.security.AccountPrincipal principal)
                || accounts.findByLoginId(authentication.getName()).filter(principal::matches).filter(account -> organizations == null || organizations.matches(principal, account)).isEmpty())) {
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8"); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"code\":\"ACCOUNT_INACTIVE\",\"message\":\"当前账号已停用或资格不可用。\",\"requestId\":\"" + requestId(request) + "\"}");
            return;
        }
        if (requests != null && authentication != null && authentication.getPrincipal() instanceof cn.voidhaven.api.security.AccountPrincipal principal
                && !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
            try { requests.write(principal, request, response, chain); }
            catch (ApiException error) {
                response.setStatus(error.status().value()); response.setCharacterEncoding("UTF-8"); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"code\":\"" + error.code() + "\",\"message\":\"操作资格已变化，请刷新后重试。\"}");
            }
        } else chain.doFilter(request, response);
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
