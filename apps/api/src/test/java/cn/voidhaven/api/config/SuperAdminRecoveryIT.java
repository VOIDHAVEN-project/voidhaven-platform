package cn.voidhaven.api.config;

import cn.voidhaven.api.VoidhavenApiApplication;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuperAdminRecoveryIT {
    @Test void non_web_context_recovers_existing_super_admin_and_rolls_back_every_change_on_audit_failure() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        try (var mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("recovery_test").withUsername("test").withPassword("test-only")) {
            mysql.start(); String beforePassword = "Synthetic-only-" + UUID.randomUUID();
            try (var app = new SpringApplicationBuilder(VoidhavenApiApplication.class).web(WebApplicationType.NONE).run(
                    "--spring.profiles.active=local", "--spring.main.web-application-type=none", "--logging.level.root=WARN",
                    "--spring.datasource.url=" + mysql.getJdbcUrl(), "--spring.datasource.username=" + mysql.getUsername(), "--spring.datasource.password=" + mysql.getPassword(),
                    "--voidhaven.bootstrap-admin-login=recovery-root", "--voidhaven.bootstrap-admin-password=" + beforePassword)) {
                assertThat(app).isNotInstanceOf(org.springframework.boot.web.server.context.WebServerApplicationContext.class);
                var accounts = app.getBean(AccountRepository.class); var encoder = app.getBean(PasswordEncoder.class);
                var jdbc = app.getBean(JdbcTemplate.class); var transactions = app.getBean(TransactionTemplate.class);
                var root = accounts.findByLoginId("recovery-root").orElseThrow(); String id = root.getId();
                String oldHash = root.getPasswordHash();
                var failedAudit = mock(AuditService.class);
                doThrow(new IllegalStateException("synthetic audit failure")).when(failedAudit).record(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
                var failing = new RecoverSuperAdminCommand(accounts, encoder, app.getBean(SecurityRevocationService.class), failedAudit, new MockEnvironment(), transactions);
                assertThatThrownBy(() -> failing.recover("recovery-root", beforePassword + "next", "synthetic-ops", "test-only")).hasMessageContaining("audit failure");
                assertThat(accounts.findById(id).orElseThrow().getPasswordHash()).isEqualTo(oldHash);
                assertThat(accounts.findById(id).orElseThrow().getSecurityEpoch()).isZero();
                var recovery = new RecoverSuperAdminCommand(accounts, encoder, app.getBean(SecurityRevocationService.class), app.getBean(AuditService.class), new MockEnvironment(), transactions);
                recovery.recover("recovery-root", beforePassword + "next", "synthetic-ops", "isolated recovery drill");
                var restored = accounts.findById(id).orElseThrow();
                assertThat(encoder.matches(beforePassword + "next", restored.getPasswordHash())).isTrue();
                assertThat(restored.getSecurityEpoch()).isEqualTo(1);
                assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'SUPER_ADMIN_RECOVERED' and object_id = ?", Integer.class, id)).isEqualTo(1);
                assertThat(accounts.count()).isEqualTo(1);
            }
        }
    }
}
