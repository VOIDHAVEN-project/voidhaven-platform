package cn.voidhaven.api.integration;

import cn.voidhaven.api.service.*;
import cn.voidhaven.api.web.ApiException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ResourceConfigPersistenceIT {
    @Test void upgrades_v24_and_serializes_configuration_writes_with_transactional_history() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(), "Docker 不可用，资源配置数据库测试未执行。");
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("resource_config_test").withUsername("test").withPassword("test-only-password")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).locations("classpath:db/migration").target("24").load().migrate();
            Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            var jdbc = new JdbcTemplate(dataSource);
            var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            var service = new ResourceConfigService(jdbc, JsonMapper.builder().build(), mock(AuditService.class));
            var initial = service.current();
            assertThat(initial.version()).isEqualTo(1);
            assertThat(initial.templates()).hasSize(5);
            var added = new java.util.ArrayList<>(initial.categories());
            added.add(new ResourceConfigService.Category("ISOLATED", "隔离实验", true));
            var input = new ResourceConfigService.Config(1, List.copyOf(added), initial.fields(), initial.templates());
            var pool = Executors.newFixedThreadPool(2);
            try {
                var barrier = new CyclicBarrier(2);
                Callable<Boolean> write = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try { tx.execute(status -> service.replace(input, 1, "test-actor", "ADMIN", "request")); return true; }
                    catch (ApiException e) { assertThat(e.code()).isEqualTo("RESOURCE_CONFIG_VERSION_CONFLICT"); return false; }
                };
                var one = pool.submit(write); var two = pool.submit(write);
                assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            } finally { pool.shutdownNow(); }
            assertThat(service.current().version()).isEqualTo(2);
            assertThat(service.history(null)).hasSize(2);
            assertThat(service.history(2L).get(0).version()).isEqualTo(1);
            assertThatThrownBy(() -> tx.execute(status -> {
                service.replace(service.current(), 2, "test-actor", "ADMIN", "request");
                throw new IllegalStateException("intentional rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(service.current().version()).isEqualTo(2);
            assertThat(service.history(null)).hasSize(2);
        }
    }
    private boolean dockerAvailable() {
        try { return DockerClientFactory.instance().isDockerAvailable(); } catch (Exception e) { return false; }
    }
}
