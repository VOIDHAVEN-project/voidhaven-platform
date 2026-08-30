package cn.voidhaven.api.service;
import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.web.*;
import tools.jackson.databind.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ContentService {
    private final ContentItemRepository items; private final ObjectMapper mapper;
    public ContentService(ContentItemRepository items, ObjectMapper mapper) { this.items = items; this.mapper = mapper; }
    public List<PublicContentDto> listPublic(ContentKind kind, int size) { return items.findPublicByKind(kind, PageRequest.of(0, Math.min(size, 50))).stream().map(this::toPublic).toList(); }
    public PublicContentDto publicDetail(ContentKind kind, String id) { return items.findPublicByIdAndKind(id, kind).map(this::toPublic).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。")); }
    public Map<String, Object> home() {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("competitions", listPublic(ContentKind.COMPETITION, 4)); result.put("resources", listPublic(ContentKind.RESOURCE, 4)); result.put("recruitments", listPublic(ContentKind.RECRUITMENT, 4)); return result;
    }
    public PublicContentDto toPublic(ContentItem item) {
        ContentRevision r = item.getPublishedRevision();
        try { return new PublicContentDto(item.getId(), item.getKind(), r.getTitle(), r.getSummary(), mapper.readTree(r.getBodyJson()), r.getLanguageCode(), r.getSourceName(), r.getSourceUrl(), r.getRightsBasis(), mapper.readTree(r.getDetailsJson()), item.getVersion() == null ? 0 : item.getVersion()); }
        catch (Exception e) { throw new IllegalStateException("Invalid persisted content document", e); }
    }
}
