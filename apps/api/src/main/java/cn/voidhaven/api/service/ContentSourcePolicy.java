package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.InetAddress;
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
                    || host.endsWith(".test") || host.endsWith(".example") || host.endsWith(".local")
                    || host.endsWith(".internal") || uri.getUserInfo() != null || isUnsafeLiteralAddress(host)) {
                return false;
            }
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private boolean isUnsafeLiteralAddress(String host) {
        boolean numeric = host.matches("[0-9.]+") || host.contains(":");
        if (!numeric) return false;
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
            byte[] bytes = address.getAddress();
            if (bytes.length == 4) {
                int first = bytes[0] & 255; int second = bytes[1] & 255; int third = bytes[2] & 255;
                return first == 0 || first >= 224 || (first == 100 && second >= 64 && second <= 127)
                        || (first == 192 && second == 0 && (third == 0 || third == 2))
                        || (first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                        || (first == 203 && second == 0 && third == 113);
            }
            return (bytes[0] & 0xfe) == 0xfc || ((bytes[0] & 255) == 0x20 && (bytes[1] & 255) == 0x01 && (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8);
        } catch (Exception error) { return true; }
    }

    private void reject() {
        throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_URL_REQUIRED", "官方或原始来源必须是可核验的 HTTPS 链接；本地合成来源仅可在本地环境使用。");
    }
}
