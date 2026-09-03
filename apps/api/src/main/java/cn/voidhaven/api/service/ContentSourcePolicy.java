package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class ContentSourcePolicy {
    private final boolean localFixturesEnabled;
    private final String localFixtureSource;

    public ContentSourcePolicy(
            @Value("${voidhaven.local-fixture-sources-enabled:false}") boolean localFixturesEnabled,
            @Value("${voidhaven.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.localFixturesEnabled = localFixturesEnabled;
        this.localFixtureSource = publicBaseUrl.replaceAll("/$", "") + "/local-fixtures/source";
    }

    public void requireAllowed(String value) {
        if (!isAllowed(value)) reject();
    }

    /**
     * Applies the same source rule to persisted public projections.  This is a
     * fail-closed guard: a legacy or manually imported invalid URL must not be
     * emitted publicly just because it bypassed a write-time validator.
     */
    public boolean isAllowed(String value) {
        if (value == null || value.isBlank()) return false;
        String source = value.trim();
        if (localFixturesEnabled && localFixtureSource.equals(source)) return true;
        try {
            URI uri = URI.create(source);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isBlank() || host.equals("localhost")
                    || host.endsWith(".localhost") || host.equals("example.invalid") || host.endsWith(".invalid")
                    || host.endsWith(".test") || host.endsWith(".example")) {
                return false;
            }
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private void reject() {
        throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_URL_REQUIRED", "官方或原始来源必须是可核验的 HTTPS 链接；本地合成来源仅可在本地环境使用。");
    }
}
