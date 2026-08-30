package cn.voidhaven.api.web;
import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.service.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/public")
public class PublicContentController {
    private final ContentService content;
    public PublicContentController(ContentService content) { this.content = content; }
    @GetMapping("/home") ApiResponse<Map<String, Object>> home(HttpServletRequest req) { return ok(content.home(), req); }
    @GetMapping("/competitions") ApiResponse<List<PublicContentDto>> competitions(@RequestParam(defaultValue = "20") int pageSize, HttpServletRequest req) { return ok(content.listPublic(ContentKind.COMPETITION, pageSize), req); }
    @GetMapping("/competitions/{id}") ApiResponse<PublicContentDto> competition(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(ContentKind.COMPETITION, id), req); }
    @GetMapping("/resources") ApiResponse<List<PublicContentDto>> resources(@RequestParam(defaultValue = "20") int pageSize, HttpServletRequest req) { return ok(content.listPublic(ContentKind.RESOURCE, pageSize), req); }
    @GetMapping("/resources/{id}") ApiResponse<PublicContentDto> resource(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(ContentKind.RESOURCE, id), req); }
    @GetMapping("/recruitments") ApiResponse<List<PublicContentDto>> recruitments(@RequestParam(defaultValue = "20") int pageSize, HttpServletRequest req) { return ok(content.listPublic(ContentKind.RECRUITMENT, pageSize), req); }
    @GetMapping("/recruitments/{id}") ApiResponse<PublicContentDto> recruitment(@PathVariable String id, HttpServletRequest req) { return ok(content.publicDetail(ContentKind.RECRUITMENT, id), req); }
    private <T> ApiResponse<T> ok(T data, HttpServletRequest req) { return new ApiResponse<>(data, (String) req.getAttribute(RequestIdFilter.ATTRIBUTE)); }
}
