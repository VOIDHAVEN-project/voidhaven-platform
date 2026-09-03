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
    private final ContentItemRepository items; private final ObjectMapper mapper; private final ContentSourcePolicy sources; private final MediaReadService media;
    public ContentService(ContentItemRepository items, ObjectMapper mapper, ContentSourcePolicy sources, MediaReadService media) { this.items = items; this.mapper = mapper; this.sources = sources; this.media = media; }
    public record PublicPage(List<PublicContentDto> items, String nextCursor) { }
    public List<PublicContentDto> listPublic(ContentKind kind, int size) { return listPublicPage(kind, size, null, null).items(); }
    public PublicPage listPublicPage(ContentKind kind, int size, String cursor, String query) {
        int pageSize = Math.max(1, Math.min(size, 50));
        String normalizedCursor = blankToNull(cursor);
        String normalizedQuery = blankToNull(query);
        List<ContentItem> rows = items.findPublicPageByKind(kind, normalizedCursor, normalizedQuery, PageRequest.of(0, pageSize + 1));
        boolean hasNext = rows.size() > pageSize;
        List<ContentItem> rawPage = hasNext ? rows.subList(0, pageSize) : rows;
        List<ContentItem> visible = rawPage.stream()
                .filter(item -> sources.isAllowed(item.getPublishedRevision().getSourceUrl()))
                .toList();
        String nextCursor = hasNext ? rawPage.get(rawPage.size() - 1).getId() : null;
        return new PublicPage(visible.stream().map(item -> toPublic(item, false)).toList(), nextCursor);
    }
    public PublicContentDto publicDetail(ContentKind kind, String id) {
        return items.findPublicByIdAndKind(id, kind)
                .filter(item -> sources.isAllowed(item.getPublishedRevision().getSourceUrl()))
                .map(item -> toPublic(item, true))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "内容不存在或当前不可见。"));
    }
    public Map<String, Object> home() {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("competitions", listPublic(ContentKind.COMPETITION, 4)); result.put("resources", listPublic(ContentKind.RESOURCE, 4)); result.put("recruitments", listPublic(ContentKind.RECRUITMENT, 4)); return result;
    }
    private PublicContentDto toPublic(ContentItem item, boolean includeMedia) {
        ContentRevision r = item.getPublishedRevision();
        try { return new PublicContentDto(item.getId(), item.getKind(), r.getTitle(), r.getSummary(), mapper.readTree(r.getBodyJson()), r.getLanguageCode(), r.getSourceName(), r.getSourceUrl(), r.getRightsBasis(), mapper.readTree(r.getDetailsJson()), item.getVersion() == null ? 0 : item.getVersion(), includeMedia ? media.forPublishedRevision(r.getId()) : List.of()); }
        catch (Exception e) { throw new IllegalStateException("Invalid persisted content document", e); }
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
