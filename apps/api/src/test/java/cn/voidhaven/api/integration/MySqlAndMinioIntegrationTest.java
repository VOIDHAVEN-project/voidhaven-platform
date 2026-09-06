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
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 引擎不可用；集成测试未执行。");
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("voidhaven_test").withUsername("voidhaven").withPassword("test-only-password");
             MySQLContainer<?> upgradeMysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("voidhaven_upgrade_test").withUsername("voidhaven").withPassword("test-only-password");
             MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z");) {
            Startables.deepStart(Stream.of(mysql, upgradeMysql, minio)).join();
            Flyway flyway = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).locations("classpath:db/migration").load();
            flyway.migrate();
            assertThat(flyway.info().applied()).hasSize(10);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");
            try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "media_deletion_jobs", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "auth_rate_limit_events", null).next()).isTrue();
                assertThat(connection.getMetaData().getColumns(mysql.getDatabaseName(), null, "content_items", "submitted_at").next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "SPRING_SESSION", null).next()).isTrue();
                assertThat(connection.getMetaData().getTables(mysql.getDatabaseName(), null, "SPRING_SESSION_ATTRIBUTES", null).next()).isTrue();
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
            Flyway upgraded = Flyway.configure()
                    .dataSource(upgradeMysql.getJdbcUrl(), upgradeMysql.getUsername(), upgradeMysql.getPassword())
                    .locations("classpath:db/migration")
                    .load();
            upgraded.migrate();
            assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("10");

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
}
