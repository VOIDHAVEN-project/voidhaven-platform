package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

public record CatalogCursor(CatalogSort sort, String key, String id, boolean legacy) {
    private static final Pattern ULID = Pattern.compile("[0-9A-HJKMNP-TV-Z]{26}");
    private static final String PREFIX = "c1.";

    public static CatalogCursor of(CatalogSort sort, String key, String id) {
        return new CatalogCursor(sort, key == null ? "" : key, id, false);
    }

    public String encode() {
        String payload = sort.name() + "|" + (key == null ? "" : key) + "|" + id;
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static CatalogCursor decode(String raw, CatalogSort expected) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.startsWith(PREFIX)) {
            try {
                String payload = new String(Base64.getUrlDecoder().decode(value.substring(PREFIX.length())), StandardCharsets.UTF_8);
                String[] parts = payload.split("\\|", 3);
                if (parts.length != 3 || parts[2].isBlank()) throw new IllegalArgumentException();
                CatalogSort sort = CatalogSort.valueOf(parts[0]);
                if (sort != expected) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_CURSOR_MISMATCH", "分页游标与当前排序不一致，请从第一页重新浏览。");
                }
                return new CatalogCursor(sort, parts[1], parts[2], false);
            } catch (ApiException error) {
                throw error;
            } catch (Exception error) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_CURSOR_INVALID", "分页游标无效。");
            }
        }
        if (ULID.matcher(value).matches() && expected == CatalogSort.DEFAULT) {
            return new CatalogCursor(CatalogSort.DEFAULT, "", value, true);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "CATALOG_CURSOR_INVALID", "分页游标无效。");
    }
}
