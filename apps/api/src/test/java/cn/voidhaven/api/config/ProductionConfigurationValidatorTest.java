package cn.voidhaven.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ProductionConfigurationValidatorTest {
    @Test
    void acceptsExplicitSecureProductionConfiguration() {
        ProductionConfigurationValidator validator = validator(
                "https://voidhaven.cn", "jdbc:mysql://mysql.internal:3306/voidhaven", "voidhaven_app",
                "a-production-database-password");

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void rejectsLocalPublicUrlAndPlaceholderDatabasePassword() {
        ProductionConfigurationValidator localUrl = validator(
                "http://localhost:8080", "jdbc:mysql://mysql.internal:3306/voidhaven", "voidhaven_app",
                "a-production-database-password");
        assertThatThrownBy(() -> localUrl.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VOIDHAVEN_PUBLIC_BASE_URL");

        ProductionConfigurationValidator placeholder = validator(
                "https://voidhaven.cn", "jdbc:mysql://mysql.internal:3306/voidhaven", "voidhaven_app",
                "change-me-database-password");
        assertThatThrownBy(() -> placeholder.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VOIDHAVEN_DB_PASSWORD");
    }

    @Test
    void rejectsRootDatabaseAccountAndTrustAllProxyRange() {
        ProductionConfigurationValidator root = validator(
                "https://voidhaven.cn", "jdbc:mysql://mysql.internal:3306/voidhaven", "root",
                "a-production-database-password");
        assertThatThrownBy(() -> root.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("least-privilege");

        ProductionConfigurationValidator trustAll = new ProductionConfigurationValidator(
                "https://voidhaven.cn", "https://cos.example.com", "media-user", "a-secure-media-secret",
                "voidhaven-media", "a-production-rate-limit-secret-of-sufficient-length", "0.0.0.0/0", true,
                "jdbc:mysql://mysql.internal:3306/voidhaven", "voidhaven_app", "a-production-database-password");
        assertThatThrownBy(() -> trustAll.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not trust every network");
    }

    private ProductionConfigurationValidator validator(String publicUrl, String databaseUrl,
            String databaseUsername, String databasePassword) {
        return new ProductionConfigurationValidator(
                publicUrl, "https://cos.example.com", "media-user", "a-secure-media-secret",
                "voidhaven-media", "a-production-rate-limit-secret-of-sufficient-length", "172.30.0.0/24", true,
                databaseUrl, databaseUsername, databasePassword);
    }
}
