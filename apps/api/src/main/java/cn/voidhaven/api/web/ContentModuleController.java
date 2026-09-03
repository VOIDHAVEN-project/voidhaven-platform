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
    public ContentModuleController(ContentModuleRegistry modules) { this.modules = modules; }
    @GetMapping ApiResponse<JsonNode> definitions(HttpServletRequest request) {
        return new ApiResponse<>(modules.publicDefinitions(), (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
