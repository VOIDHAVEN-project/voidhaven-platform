package cn.voidhaven.api.service;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.security.AccountPrincipal;
import cn.voidhaven.api.web.ApiException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Holds eligibility locks through the actual business transaction, including idempotent replay. */
@Service
public class RequestSecurityService {
    private final AccountRepository accounts;
    private final TransactionTemplate transactions;
    private final OrganizationAccessService organizations;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final Pattern TARGET = Pattern.compile("/api/v1/(?:admin/accounts|partner/members)/([^/]+)(?:/.*)?");
    private static final Pattern CONTENT = Pattern.compile("/api/v1/(?:admin|partner)/(?:content-items|reviews)/([^/]+)(?:/.*)?");
    private static final Pattern ORGANIZATION = Pattern.compile("/api/v1/admin/partner-profiles/([^/]+)(?:/.*)?");
    public RequestSecurityService(AccountRepository accounts, TransactionTemplate transactions, OrganizationAccessService organizations, JdbcTemplate jdbc, ObjectMapper json) {
        this.accounts = accounts; this.transactions = transactions; this.organizations = organizations; this.jdbc = jdbc; this.json = json;
    }
    public void write(AccountPrincipal principal, HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        // Only this small transfer body is buffered; credentials and upload streams are never buffered here.
        HttpServletRequest effective = request.getRequestURI().equals("/api/v1/admin/content-ownership-transfers") ? new TransferRequest(request) : request;
        try {
            transactions.executeWithoutResult(status -> {
                TreeSet<String> ids = new TreeSet<>(); ids.add(principal.accountId());
                TreeSet<String> profileIds = new TreeSet<>();
                String actorProfile = organizations.organizationId(principal.accountId());
                if (actorProfile != null) profileIds.add(actorProfile);
                var match = TARGET.matcher(request.getRequestURI());
                String target = match.matches() ? match.group(1) : null;
                if (target != null) { ids.add(target); String profile = organizations.organizationId(target); if (profile != null) profileIds.add(profile); }
                var organizationMatch = ORGANIZATION.matcher(request.getRequestURI());
                if (organizationMatch.matches()) {
                    String profile = organizationMatch.group(1); profileIds.add(profile);

                }
                var contentMatch = CONTENT.matcher(request.getRequestURI());
                String contentId = contentMatch.matches() ? contentMatch.group(1) : request.getRequestURI().startsWith("/api/v1/media/") ? request.getParameter("contentItemId") : null;
                if (effective instanceof TransferRequest transfer) {
                    tools.jackson.databind.JsonNode body;
                    try { body = json.readTree(transfer.body); }
                    catch (tools.jackson.core.JacksonException error) { throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_BODY_INVALID", "请求内容格式不正确。"); }
                    if (body == null || !body.isObject()) throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_BODY_INVALID", "请求内容格式不正确。");
                    contentId = body.path("contentItemId").asText(); String destination = body.path("toPartnerProfileId").asText();
                    if (!destination.isBlank()) profileIds.add(destination);
                }
                String initialOwner = contentId == null ? null : contentOwner(contentId, false);
                if (initialOwner != null) profileIds.add(initialOwner);
                for (String profile : profileIds) organizations.lock(profile);
                if (organizationMatch.matches() && (request.getRequestURI().endsWith("/suspend") || request.getRequestURI().endsWith("/reactivate"))) ids.addAll(organizations.accounts(organizationMatch.group(1)));
                boolean selfWrite = request.getRequestURI().equals("/api/v1/auth/password/change");
                for (String id : ids) {
                    var account = (id.equals(target) || selfWrite ? accounts.findByIdForUpdate(id) : accounts.findByIdForShare(id));
                    if (id.equals(principal.accountId()) && account.filter(principal::matches).filter(a -> organizations.matches(principal, a)).isEmpty())
                        throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "会话已失效，请重新登录。");
                }
                if (contentId != null && actorProfile != null && !actorProfile.equals(initialOwner))
                    throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。");
                // Ownership can change while waiting for organization locks. Retry instead of writing under stale locks.
                if (contentId != null && !Objects.equals(initialOwner, contentOwner(contentId, true)))
                    throw new ApiException(HttpStatus.CONFLICT, "CONTENT_OWNERSHIP_CHANGED", "内容归属已变化，请刷新后重试。");
                try { chain.doFilter(effective, response); }
                catch (IOException | ServletException error) { throw new FilterFailure(error); }
                if (response.getStatus() >= 400) status.setRollbackOnly();
            });
        } catch (FilterFailure failure) {
            if (failure.getCause() instanceof IOException error) throw error;
            throw (ServletException) failure.getCause();
        }
    }
    private String contentOwner(String id, boolean lock) {
        return jdbc.query("select owner_partner_profile_id from content_items where id = ?" + (lock ? " for update" : ""), row -> row.next() ? row.getString(1) : null, id);
    }
    private static class TransferRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        TransferRequest(HttpServletRequest request) throws IOException {
            super(request); body = request.getInputStream().readNBytes(16385);
            if (body.length > 16384) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "TRANSFER_BODY_TOO_LARGE", "归属转交请求过大。");
        }
        @Override public ServletInputStream getInputStream() {
            var stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public int read() { return stream.read(); }
                public boolean isFinished() { return stream.available() == 0; }
                public boolean isReady() { return true; }
                public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
    private static class FilterFailure extends RuntimeException { FilterFailure(Exception cause) { super(cause); } }
}
