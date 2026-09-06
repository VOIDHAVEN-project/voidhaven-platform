package cn.voidhaven.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Fails closed when the production profile is started with placeholder or unsafe values. */
@Component
@Profile("production")
public class ProductionConfigurationValidator implements ApplicationRunner {
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private final String publicBaseUrl;
    private final String mediaEndpoint;
    private final String mediaAccessKey;
    private final String mediaSecretKey;
    private final String mediaBucket;
    private final String rateLimitSecret;
    private final String trustedProxyNetworks;
    private final boolean cookieSecure;
    private final String databaseUrl;
    private final String databaseUsername;
    private final String databasePassword;

    public ProductionConfigurationValidator(
            @Value("${voidhaven.public-base-url:}") String publicBaseUrl,
            @Value("${voidhaven.media-endpoint:}") String mediaEndpoint,
            @Value("${voidhaven.media-access-key:}") String mediaAccessKey,
            @Value("${voidhaven.media-secret-key:}") String mediaSecretKey,
            @Value("${voidhaven.media-bucket:}") String mediaBucket,
            @Value("${voidhaven.public-write-rate-limit-secret:}") String rateLimitSecret,
            @Value("${voidhaven.trusted-proxy-networks:}") String trustedProxyNetworks,
            @Value("${voidhaven.cookie-secure:false}") boolean cookieSecure,
            @Value("${spring.datasource.url:}") String databaseUrl,
            @Value("${spring.datasource.username:}") String databaseUsername,
            @Value("${spring.datasource.password:}") String databasePassword) {
        this.publicBaseUrl = publicBaseUrl;
        this.mediaEndpoint = mediaEndpoint;
        this.mediaAccessKey = mediaAccessKey;
        this.mediaSecretKey = mediaSecretKey;
        this.mediaBucket = mediaBucket;
        this.rateLimitSecret = rateLimitSecret;
        this.trustedProxyNetworks = trustedProxyNetworks;
        this.cookieSecure = cookieSecure;
        this.databaseUrl = databaseUrl;
        this.databaseUsername = databaseUsername;
        this.databasePassword = databasePassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireHttps(publicBaseUrl, "VOIDHAVEN_PUBLIC_BASE_URL");
        requireHttps(mediaEndpoint, "VOIDHAVEN_MEDIA_ENDPOINT");
        requireSecret(mediaAccessKey, "VOIDHAVEN_MEDIA_ACCESS_KEY", 8);
        requireSecret(mediaSecretKey, "VOIDHAVEN_MEDIA_SECRET_KEY", 16);
        requireSecret(rateLimitSecret, "VOIDHAVEN_RATE_LIMIT_SECRET", 32);
        requireText(mediaBucket, "VOIDHAVEN_MEDIA_BUCKET");
        requireText(trustedProxyNetworks, "VOIDHAVEN_TRUSTED_PROXY_NETWORKS");
        if (trustedProxyNetworks.contains("0.0.0.0/0") || trustedProxyNetworks.contains("::/0")) {
            throw invalid("VOIDHAVEN_TRUSTED_PROXY_NETWORKS must not trust every network");
        }
        if (!cookieSecure) throw invalid("Production session cookies must be Secure");
        validateDatabase();
    }

    private void validateDatabase() {
        requireText(databaseUrl, "VOIDHAVEN_DB_URL");
        String normalized = databaseUrl.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("jdbc:mysql://") || normalized.contains("localhost")
                || normalized.contains("127.0.0.1") || normalized.contains("example.invalid")) {
            throw invalid("VOIDHAVEN_DB_URL must target the approved non-local MySQL service");
        }
        requireText(databaseUsername, "VOIDHAVEN_DB_USERNAME");
        if ("root".equalsIgnoreCase(databaseUsername.trim())) {
            throw invalid("VOIDHAVEN_DB_USERNAME must be a least-privilege account");
        }
        requireSecret(databasePassword, "VOIDHAVEN_DB_PASSWORD", 16);
    }

    private void requireHttps(String value, String name) {
        requireText(value, name);
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw invalid(name + " must be a valid HTTPS URL");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                || host.endsWith(".invalid") || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw invalid(name + " must be an approved HTTPS URL without credentials or fragments");
        }
    }

    private void requireSecret(String value, String name, int minimumLength) {
        requireText(value, name);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() < minimumLength || normalized.contains("change-me") || normalized.contains("inject_from")) {
            throw invalid(name + " must be injected with a non-placeholder value");
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw invalid(name + " is required");
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException("Unsafe production configuration: " + message);
    }
}
