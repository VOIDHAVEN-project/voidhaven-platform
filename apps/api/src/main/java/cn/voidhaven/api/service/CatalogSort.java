package cn.voidhaven.api.service;

import cn.voidhaven.api.domain.ContentKind;
import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;

public enum CatalogSort {
    DEFAULT,
    DEADLINE,
    UPDATED;

    public String wire() {
        return name().toLowerCase();
    }

    public static CatalogSort parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.trim().toLowerCase()) {
            case "default" -> DEFAULT;
            case "deadline" -> DEADLINE;
            case "updated" -> UPDATED;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_SORT_INVALID", "目录排序方式无效。");
        };
    }

    public static CatalogSort resolve(ContentKind kind, String requested, CatalogSort configured) {
        CatalogSort explicit = parse(requested);
        CatalogSort resolved = explicit != null ? explicit : (configured == null ? DEFAULT : configured);
        if (resolved == DEADLINE && kind == ContentKind.RESOURCE) {
            if (explicit != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_SORT_UNSUPPORTED", "学习资源不支持按截止日期排序。");
            }
            return UPDATED;
        }
        return resolved;
    }
}
