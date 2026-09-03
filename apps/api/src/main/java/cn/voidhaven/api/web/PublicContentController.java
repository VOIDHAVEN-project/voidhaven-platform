package cn.voidhaven.api.web;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.service.ContentModuleRegistry;
import cn.voidhaven.api.service.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/public")
public class PublicContentController {
    private final ContentService content;
    private final ContentModuleRegistry modules;
    public PublicContentController(ContentService content, ContentModuleRegistry modules) { this.content = content; this.modules = modules; }
    @GetMapping("/home") ApiResponse<Map<String, Object>> home(HttpServletRequest req) { return ok(content.home(), req); }
    @GetMapping("/content-modules") ApiResponse<tools.jackson.databind.JsonNode> moduleDefinitions(HttpServletRequest req) { return ok(modules.publicDefinitions(), req); }
    @GetMapping("/content") ApiResponse<ContentService.PublicPage> content(@RequestParam String module, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String cursor, @RequestParam(required = false, name = "q") String query, @RequestParam(required = false) String filterKey, @RequestParam(required = false) String filterValue, HttpServletRequest req) { return ok(content.listPublicModule(module, pageSize, cursor, query, filterKey, filterValue), req); }
    @GetMapping("/content/{id}") ApiResponse<PublicContentDto> detail(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(id), req); }
    @GetMapping("/competitions") ApiResponse<ContentService.PublicPage> competitions(@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String cursor, @RequestParam(required = false, name = "q") String query, HttpServletRequest req) { return ok(content.listPublicPage(ContentKind.COMPETITION, pageSize, cursor, query), req); }
    @GetMapping("/competitions/{id}") ApiResponse<PublicContentDto> competition(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(ContentKind.COMPETITION, id), req); }
    @GetMapping("/resources") ApiResponse<ContentService.PublicPage> resources(@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String cursor, @RequestParam(required = false, name = "q") String query, HttpServletRequest req) { return ok(content.listPublicPage(ContentKind.RESOURCE, pageSize, cursor, query), req); }
    @GetMapping("/resources/{id}") ApiResponse<PublicContentDto> resource(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(ContentKind.RESOURCE, id), req); }
    @GetMapping("/recruitments") ApiResponse<ContentService.PublicPage> recruitments(@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String cursor, @RequestParam(required = false, name = "q") String query, HttpServletRequest req) { return ok(content.listPublicPage(ContentKind.RECRUITMENT, pageSize, cursor, query), req); }
    @GetMapping("/recruitments/{id}") ApiResponse<PublicContentDto> recruitment(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(ContentKind.RECRUITMENT, id), req); }
    private <T> ApiResponse<T> ok(T data, HttpServletRequest req) { return new ApiResponse<>(data, (String) req.getAttribute(RequestIdFilter.ATTRIBUTE)); }
}
