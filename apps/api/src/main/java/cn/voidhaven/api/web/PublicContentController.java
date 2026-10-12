package cn.voidhaven.api.web;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.service.ContentModuleRegistry;
import cn.voidhaven.api.service.ContentService;
import cn.voidhaven.api.service.HomepageProjectionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/public")
public class PublicContentController {
    private final ContentService content;
    private final ContentModuleRegistry modules;
    private final cn.voidhaven.api.service.ContentActionService actions;
    private final HomepageProjectionService homepage;
    public PublicContentController(ContentService content, ContentModuleRegistry modules, cn.voidhaven.api.service.ContentActionService actions, HomepageProjectionService homepage) {
        this.content = content;
        this.modules = modules;
        this.actions = actions;
        this.homepage = homepage;
    }
    @GetMapping("/home") ApiResponse<HomepageProjectionService.HomeSections> home(HttpServletRequest req) { return ok(homepage.projectLive(), req); }
    @GetMapping("/content-modules") ApiResponse<tools.jackson.databind.JsonNode> moduleDefinitions(HttpServletRequest req) { return ok(modules.publicDefinitions(), req); }
    @GetMapping("/content") ApiResponse<ContentService.PublicPage> content(@RequestParam String module, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String cursor, @RequestParam(required = false, name = "q") String query, @RequestParam(required = false) String sort, @RequestParam(required = false) String filterKey, @RequestParam(required = false) String filterValue, @RequestParam(required = false) String status, HttpServletRequest req) { return ok(content.listPublicModule(module, pageSize, cursor, query, sort, filterKey, filterValue, status), req); }
    @GetMapping("/content/{id}") ApiResponse<PublicContentDto> detail(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(id), req); }
    @GetMapping("/content/{id}/related") ApiResponse<ContentService.PublicPage> related(@PathVariable String id, HttpServletRequest req) { return ok(content.related(id), req); }
    @GetMapping("/content/{id}/gated-actions") ApiResponse<List<cn.voidhaven.api.service.ContentActionService.PublicAction>> gatedActions(@PathVariable String id, @RequestHeader("X-Community-Grant") String grant, HttpServletRequest req) { return ok(actions.gatedActions(id, grant), req); }
    @GetMapping("/competitions") ApiResponse<ContentService.PublicPage> competitions(@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String cursor, @RequestParam(required = false, name = "q") String query, @RequestParam(required = false) String sort, @RequestParam(required = false) String filterKey, @RequestParam(required = false) String filterValue, @RequestParam(required = false) String status, HttpServletRequest req) { return ok(content.listCatalog(ContentKind.COMPETITION, null, pageSize, cursor, query, sort, filterKey, filterValue, status), req); }
    @GetMapping("/competitions/{id}") ApiResponse<PublicContentDto> competition(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(ContentKind.COMPETITION, id), req); }
    @GetMapping("/resources") ApiResponse<ContentService.PublicPage> resources(@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String cursor, @RequestParam(required = false, name = "q") String query, @RequestParam(required = false) String sort, @RequestParam(required = false) String filterKey, @RequestParam(required = false) String filterValue, @RequestParam(required = false) String status, HttpServletRequest req) { return ok(content.listCatalog(ContentKind.RESOURCE, null, pageSize, cursor, query, sort, filterKey, filterValue, status), req); }
    @GetMapping("/resources/{id}") ApiResponse<PublicContentDto> resource(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(ContentKind.RESOURCE, id), req); }
    @GetMapping("/recruitments") ApiResponse<ContentService.PublicPage> recruitments(@RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String cursor, @RequestParam(required = false, name = "q") String query, @RequestParam(required = false) String sort, @RequestParam(required = false) String filterKey, @RequestParam(required = false) String filterValue, @RequestParam(required = false) String status, HttpServletRequest req) { return ok(content.listCatalog(ContentKind.RECRUITMENT, null, pageSize, cursor, query, sort, filterKey, filterValue, status), req); }
    @GetMapping("/recruitments/{id}") ApiResponse<PublicContentDto> recruitment(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(ContentKind.RECRUITMENT, id), req); }
    private <T> ApiResponse<T> ok(T data, HttpServletRequest req) { return new ApiResponse<>(data, (String) req.getAttribute(RequestIdFilter.ATTRIBUTE)); }
}
