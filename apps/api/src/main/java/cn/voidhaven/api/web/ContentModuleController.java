package cn.voidhaven.api.web;

import cn.voidhaven.api.service.ContentModuleRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/content-modules")
@PreAuthorize("hasAnyRole('PARTNER','ADMIN','SUPER_ADMIN')")
public class ContentModuleController {
    private final ContentModuleRegistry modules;
    private cn.voidhaven.api.service.AdminAuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    void authorization(cn.voidhaven.api.service.AdminAuthorizationService authorization) { this.authorization = authorization; }
    public ContentModuleController(ContentModuleRegistry modules) { this.modules = modules; }
    @GetMapping ApiResponse<JsonNode> definitions(HttpServletRequest request) {
        var definitions = modules.definitions(true);
        if (authorization.isAdmin() && !authorization.isSuper()) {
            var filtered = ((tools.jackson.databind.node.ObjectNode) definitions).putArray("modules");
            for (var module : modules.definitions(true).path("modules")) {
                String key = module.path("key").asText();
                boolean allowed = authorization.allows("CONTENT_READ", key, null)
                        || authorization.effective().stream().anyMatch(grant -> grant.permission().startsWith("CONTENT_") && grant.scopeType().equals("ORGANIZATIONS"));
                if (allowed) filtered.add(module);
            }
        }
        return new ApiResponse<>(definitions, (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
