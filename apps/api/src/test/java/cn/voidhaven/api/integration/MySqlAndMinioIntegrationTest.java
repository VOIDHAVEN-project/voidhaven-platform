package cn.voidhaven.api.integration;

import cn.voidhaven.api.service.MediaStorage;
import cn.voidhaven.api.service.S3MediaStorage;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.sql.DriverManager;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class MySqlAndMinioIntegrationTest {
    @Test void migrates_an_empty_mysql_database_and_uses_a_private_s3_compatible_bucket() {
        Assumptions.assumeTrue(dockerAvailable(), "Docker 引擎不可用；集成测试未执行。");
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("voidhaven_test").withUsername("voidhaven").withPassword("test-only-password");
             MySQLContainer<?> upgradeMysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("voidhaven_upgrade_test").withUsername("voidhaven").withPassword("test-only-password");
             MinIOContainer minio = new MinIOContainer(
                     DockerImageName.parse("coollabsio/minio:RELEASE.2025-04-22T22-12-26Z")
                             .asCompatibleSubstituteFor("minio/minio"));) {
            Startables.deepStart(Stream.of(mysql, upgradeMysql, minio)).join();
            Flyway flyway = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).locations("classpath:db/migration").load();
            flyway.migrate();
            assertThat(flyway.info().applied()).hasSize(28);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("28");
            try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "media_deletion_jobs", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "auth_rate_limit_events", null).next()).isTrue();
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "content_items", "submitted_at").next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "rate_limit_locks", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "public_write_rate_limit_events", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "public_status_query_rate_limit_events", null).next()).isTrue();
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "accounts", "version").next()).isTrue();
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "media_deletion_jobs", "locked_by").next()).isTrue();
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "idempotency_records", "response_replayable").next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "SPRING_SESSION", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "SPRING_SESSION_ATTRIBUTES", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "content_action_entries", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "public_case_status_history", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "community_entries", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "project_join_entries", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "manual_delivery_records", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "catalog_order_entries", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "catalog_sort_settings", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "resource_config_versions", null).next()).isTrue();
                try (var config = connection.createStatement().executeQuery("select config_json from resource_config_versions where version = 1")) {
                    assertThat(config.next()).isTrue();
                    var parsed = tools.jackson.databind.json.JsonMapper.builder().build().readTree(config.getString(1));
                    assertThat(parsed.path("templates").get(0).path("markdown").asText()).contains("\n");
                    assertThat(parsed.path("categories").size()).isEqualTo(14);
                }
                try (var sorts = connection.createStatement().executeQuery("select count(*) from catalog_sort_settings")) {
                    sorts.next();
                    assertThat(sorts.getInt(1)).isEqualTo(3);
                }
                try (var curation = connection.createStatement().executeQuery("select id, version from homepage_curation")) {
                    assertThat(curation.next()).isTrue();
                    assertThat(curation.getString(1)).isEqualTo("01JX000H0MEPAGESTATE000001");
                    assertThat(curation.getLong(2)).isZero();
                }
                try (var check = connection.createStatement().executeQuery("select CHECK_CLAUSE from information_schema.CHECK_CONSTRAINTS where CONSTRAINT_SCHEMA = database() and CONSTRAINT_NAME = 'chk_homepage_placement_slot'")) {
                    assertThat(check.next()).isTrue();
                    assertThat(check.getString(1)).contains("COMPETITION");
                    assertThat(check.getString(1)).contains("FOCUS");
                }
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "activation_tokens", "token_type").next()).isTrue();
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "community_entries", "primary_slot").next()).isTrue();
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "content_action_entries", "presentation_type").next()).isTrue();
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "community_entries", "contact_channel").next()).isTrue();
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "project_join_entries", "access_level").next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "content_action_media_reservations", null).next()).isTrue();
                try (var defaults = connection.createStatement().executeQuery("select COLUMN_DEFAULT from information_schema.columns where table_schema = database() and table_name = 'content_submissions' and column_name = 'state'")) {
                    defaults.next();
                    assertThat(defaults.getString(1)).contains("RECEIVED");
                }
                try (var roles = connection.createStatement().executeQuery("select count(*) from roles")) {
                    roles.next();
                    assertThat(roles.getInt(1)).isEqualTo(3);
                }
            } catch (Exception error) {
                throw new AssertionError("Flyway schema evidence could not be inspected", error);
            }

            Flyway v7 = Flyway.configure()
                    .dataSource(upgradeMysql.getJdbcUrl(), upgradeMysql.getUsername(), upgradeMysql.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("7"))
                    .load();
            v7.migrate();
            assertThat(v7.info().current().getVersion().getVersion()).isEqualTo("7");
            Flyway toV11 = Flyway.configure()
                    .dataSource(upgradeMysql.getJdbcUrl(), upgradeMysql.getUsername(), upgradeMysql.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("11"))
                    .load();
            toV11.migrate();
            assertThat(toV11.info().current().getVersion().getVersion()).isEqualTo("11");
            try (var connection = DriverManager.getConnection(upgradeMysql.getJdbcUrl(), upgradeMysql.getUsername(), upgradeMysql.getPassword());
                 var statement = connection.createStatement()) {
                statement.executeUpdate("insert into content_submissions (id, submission_type, title, message, state) values ('01JSUBMISSION0000000000001', 'FEEDBACK', 'legacy', 'legacy row', 'PENDING_REVIEW')");
                statement.executeUpdate("insert into partner_applications (id, display_name, introduction, state) values ('01JPARTNERAPP0000000000001', 'legacy partner', 'legacy application', 'PENDING_REVIEW')");
            } catch (Exception error) {
                throw new AssertionError("V11 fixture rows could not be inserted", error);
            }
            Flyway upgraded = Flyway.configure()
                    .dataSource(upgradeMysql.getJdbcUrl(), upgradeMysql.getUsername(), upgradeMysql.getPassword())
                    .locations("classpath:db/migration")
                    .load();
            upgraded.migrate();
            assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("28");
            try (var connection = DriverManager.getConnection(upgradeMysql.getJdbcUrl(), upgradeMysql.getUsername(), upgradeMysql.getPassword());
                 var migrated = connection.createStatement().executeQuery("select state from content_submissions where id = '01JSUBMISSION0000000000001'")) {
                migrated.next();
                assertThat(migrated.getString(1)).isEqualTo("RECEIVED");
                try (var partner = connection.createStatement().executeQuery("select state from partner_applications where id = '01JPARTNERAPP0000000000001'")) {
                    partner.next();
                    assertThat(partner.getString(1)).isEqualTo("RECEIVED");
                }
                try (var check = connection.createStatement().executeQuery("select CHECK_CLAUSE from information_schema.CHECK_CONSTRAINTS where CONSTRAINT_SCHEMA = database() and CONSTRAINT_NAME = 'chk_homepage_placement_slot'")) {
                    assertThat(check.next()).isTrue();
                    assertThat(check.getString(1)).contains("COMPETITION");
                }
                try (var curation = connection.createStatement().executeQuery("select count(*) from homepage_curation where id = '01JX000H0MEPAGESTATE000001'")) {
                    curation.next();
                    assertThat(curation.getInt(1)).isEqualTo(1);
                }
                try (var catalog = connection.createStatement().executeQuery("select count(*) from catalog_sort_settings")) {
                    catalog.next();
                    assertThat(catalog.getInt(1)).isEqualTo(3);
                }
            } catch (Exception error) {
                throw new AssertionError("V11 to V25 public-case and entry-model upgrade could not be inspected", error);
            }

            String bucket = "voidhaven-media-test";
            try (S3Client s3 = S3Client.builder().endpointOverride(URI.create(minio.getS3URL())).region(Region.of("us-east-1"))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                    .forcePathStyle(true).build()) {
                s3.createBucket(request -> request.bucket(bucket));
                MediaStorage storage = new S3MediaStorage(s3, bucket);
                storage.put("candidate/asset.png", "image/png", new byte[] {1, 2, 3});
                assertThat(storage.get("candidate/asset.png").bytes()).containsExactly(1, 2, 3);
                storage.delete("candidate/asset.png");
            }
        }
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
