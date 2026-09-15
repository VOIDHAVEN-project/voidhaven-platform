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
                "jdbc:mysql://mysql.internal:3306/voidhaven", "voidhaven_app", "a-production-database-password",
                false, "", "", false, 30, "");
        assertThatThrownBy(() -> trustAll.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not trust every network");
    }

    private ProductionConfigurationValidator validator(String publicUrl, String databaseUrl,
            String databaseUsername, String databasePassword) {
        return new ProductionConfigurationValidator(
                publicUrl, "https://cos.example.com", "media-user", "a-secure-media-secret",
                "voidhaven-media", "a-production-rate-limit-secret-of-sufficient-length", "172.30.0.0/24", true,
                databaseUrl, databaseUsername, databasePassword,
                false, "", "", false, 30, "");
    }

    @Test
    void rejectsEnabledPrivateContactOrCommunityGateWithoutProductionSecrets() {
        ProductionConfigurationValidator privateContact = new ProductionConfigurationValidator(
                "https://voidhaven.cn", "https://cos.example.com", "media-user", "a-secure-media-secret",
                "voidhaven-media", "a-production-rate-limit-secret-of-sufficient-length", "172.30.0.0/24", true,
                "jdbc:mysql://mysql.internal:3306/voidhaven", "voidhaven_app", "a-production-database-password",
                true, "local-dev", "", false, 30, "");
        assertThatThrownBy(() -> privateContact.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VOIDHAVEN_PRIVATE_VALUE_CURRENT_KEY_ID");

        ProductionConfigurationValidator community = new ProductionConfigurationValidator(
                "https://voidhaven.cn", "https://cos.example.com", "media-user", "a-secure-media-secret",
                "voidhaven-media", "a-production-rate-limit-secret-of-sufficient-length", "172.30.0.0/24", true,
                "jdbc:mysql://mysql.internal:3306/voidhaven", "voidhaven_app", "a-production-database-password",
                false, "", "", true, 30, "local-development-community-secret");
        assertThatThrownBy(() -> community.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VOIDHAVEN_COMMUNITY_INVITE_HMAC_SECRET");

        ProductionConfigurationValidator communityWithoutKeys = new ProductionConfigurationValidator(
                "https://voidhaven.cn", "https://cos.example.com", "media-user", "a-secure-media-secret",
                "voidhaven-media", "a-production-rate-limit-secret-of-sufficient-length", "172.30.0.0/24", true,
                "jdbc:mysql://mysql.internal:3306/voidhaven", "voidhaven_app", "a-production-database-password",
                false, "", "", true, 30, "a-production-community-invite-hmac-secret-long");
        assertThatThrownBy(() -> communityWithoutKeys.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VOIDHAVEN_PRIVATE_VALUE_CURRENT_KEY_ID");
    }

    @Test
    void acceptsCommunityGateWithIndependentProductionKeyRing() {
        ProductionConfigurationValidator community = new ProductionConfigurationValidator(
                "https://voidhaven.cn", "https://cos.example.com", "media-user", "a-secure-media-secret",
                "voidhaven-media", "a-production-rate-limit-secret-of-sufficient-length", "172.30.0.0/24", true,
                "jdbc:mysql://mysql.internal:3306/voidhaven", "voidhaven_app", "a-production-database-password",
                false, "prod-v1", "prod-v1:dGVzdC1vbmx5LXByaXZhdGUtdmFsdWUta2V5LTMyISE=",
                true, 30, "a-production-community-invite-hmac-secret-long");

        assertThatCode(() -> community.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

}
