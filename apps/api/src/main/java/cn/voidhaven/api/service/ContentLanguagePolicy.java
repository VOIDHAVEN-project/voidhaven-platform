package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ContentLanguagePolicy {
    private static final Map<String, String> LABELS = Map.of(
            "zh-CN", "中文",
            "en", "英文",
            "zh-CN,en", "中英双语");

    public String normalize(String value) {
        String language = value == null ? "" : value.trim();
        if (LABELS.containsKey(language)) return language;
        if (language.matches("other:[A-Za-z][A-Za-z -]{1,29}")) return language;
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTENT_LANGUAGE", "内容语言仅支持中文、英文、中英双语，或 other:具体语种。 ");
    }

    public boolean requiresOriginalTitle(String language) { return !"zh-CN".equals(language); }

    public String label(String language) {
        String normalized = normalize(language);
        String label = LABELS.get(normalized);
        return label != null ? label : "其他语言（" + normalized.substring("other:".length()) + "）";
    }
}
