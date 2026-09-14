package cn.voidhaven.api.integration;

import cn.voidhaven.api.service.AuthenticationRateLimiter;
import cn.voidhaven.api.service.AuditService;
import cn.voidhaven.api.service.CommunityService;
import cn.voidhaven.api.service.MysqlAdvisoryLock;
import cn.voidhaven.api.service.PrivateValueCipher;
import cn.voidhaven.api.service.RateLimitLockService;
import cn.voidhaven.api.support.SecureTokens;
import cn.voidhaven.api.web.ApiException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("integration")
class DistributedSafetyIT {
    @Test void serializes_rate_limits_advisory_locks_and_media_leases() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(), "Docker 引擎不可用；集成测试未执行。");
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("voidhaven_test").withUsername("voidhaven").withPassword("test-only-password")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).locations("classpath:db/migration").load().migrate();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
            RateLimitLockService locks = new RateLimitLockService(jdbc, transactions);
            AuthenticationRateLimiter auth = new AuthenticationRateLimiter(locks, "test-secret", 5, 20, 10, 3, 10, 15);

            ExecutorService pool = Executors.newFixedThreadPool(8);
            CyclicBarrier start = new CyclicBarrier(8);
            AtomicInteger allowed = new AtomicInteger();
            AtomicInteger limited = new AtomicInteger();
            List<Future<?>> publicWrites = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                publicWrites.add(pool.submit(() -> {
                    start.await();
                    try {
                        locks.reservePublicWrite("subject-a", 2, Duration.ofHours(1));
                        allowed.incrementAndGet();
                    } catch (ApiException error) {
                        if ("PUBLIC_WRITE_RATE_LIMITED".equals(error.code())) limited.incrementAndGet();
                        else throw error;
                    }
                    return null;
                }));
            }
            for (Future<?> future : publicWrites) future.get(20, TimeUnit.SECONDS);
            assertThat(allowed.get()).isEqualTo(2);
            assertThat(limited.get()).isEqualTo(6);

            locks.reservePublicStatusQuery("status-subject", 2, Duration.ofHours(1));
            locks.reservePublicStatusQuery("status-subject", 2, Duration.ofHours(1));
            try {
                locks.reservePublicStatusQuery("status-subject", 2, Duration.ofHours(1));
                throw new AssertionError("status-query limiter should reject the third request");
            } catch (ApiException error) {
                assertThat(error.code()).isEqualTo("PUBLIC_STATUS_QUERY_RATE_LIMITED");
            }
            Integer publicWriteCount = jdbc.queryForObject(
                    "select count(*) from public_write_rate_limit_events where subject_hash = 'status-subject'", Integer.class);
            assertThat(publicWriteCount).isZero();

            CyclicBarrier loginStart = new CyclicBarrier(8);
            AtomicInteger loginAllowed = new AtomicInteger();
            AtomicInteger loginLimited = new AtomicInteger();
            List<Future<?>> logins = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                logins.add(pool.submit(() -> {
                    loginStart.await();
                    try {
                        auth.beginLogin("admin.example", "203.0.113.10");
                        loginAllowed.incrementAndGet();
                    } catch (ApiException error) {
                        if ("AUTH_RATE_LIMITED".equals(error.code())) loginLimited.incrementAndGet();
                        else throw error;
                    }
                    return null;
                }));
            }
            for (Future<?> future : logins) future.get(20, TimeUnit.SECONDS);
            assertThat(loginAllowed.get()).isEqualTo(5);
            assertThat(loginLimited.get()).isEqualTo(3);

            jdbc.update("delete from auth_rate_limit_events where scope = 'LOGIN_ID'");
            String failedBeforeSuccess = auth.beginLogin("completion.example", "203.0.113.11");
            String successfulAttempt = auth.beginLogin("completion.example", "203.0.113.12");
            auth.loginFailed(failedBeforeSuccess);
            auth.loginSucceeded(successfulAttempt);
            Integer failuresAfterSuccess = jdbc.queryForObject(
                    "select count(*) from auth_rate_limit_events where scope = 'LOGIN_ID' and outcome = 'FAILURE'", Integer.class);
            assertThat(failuresAfterSuccess).isZero();

            String failedAfterSuccess = auth.beginLogin("completion.example", "203.0.113.13");
            auth.loginSucceeded(successfulAttempt);
            auth.loginFailed(failedAfterSuccess);
            Integer failuresAfterDuplicateCompletion = jdbc.queryForObject(
                    "select count(*) from auth_rate_limit_events where scope = 'LOGIN_ID' and outcome = 'FAILURE'", Integer.class);
            assertThat(failuresAfterDuplicateCompletion).isEqualTo(1);

            String communityActor = cn.voidhaven.api.support.Ids.ulid();
            jdbc.update("insert into accounts (id, login_id, password_hash, qualification_status) values (?, 'community.owner', 'x', 'ACTIVE')", communityActor);
            String inviteCode = "test-shared-invite-code";
            String communitySecret = "test-community-hmac-secret";
            jdbc.update("insert into community_invite_batches (id, label, code_hmac, valid_from, expires_at, max_uses, created_by_account_id) values (?, 'concurrency', ?, current_timestamp(6), date_add(current_timestamp(6), interval 1 day), 2, ?)",
                    "01JINVITE00000000000000000", SecureTokens.hmacSha256(communitySecret, inviteCode), communityActor);
            PrivateValueCipher privateValues = mock(PrivateValueCipher.class);
            CommunityService community = new CommunityService(jdbc, privateValues, mock(AuditService.class), true,
                    30, communitySecret, new MockEnvironment());
            CyclicBarrier inviteStart = new CyclicBarrier(8);
            AtomicInteger inviteAllowed = new AtomicInteger();
            AtomicInteger inviteRejected = new AtomicInteger();
            List<Future<?>> inviteVerifications = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int participant = i;
                inviteVerifications.add(pool.submit(() -> {
                    inviteStart.await();
                    try {
                        community.verify(inviteCode, "203.0.113." + participant);
                        inviteAllowed.incrementAndGet();
                    } catch (ApiException error) {
                        if ("COMMUNITY_INVITE_INVALID".equals(error.code())) inviteRejected.incrementAndGet();
                        else throw error;
                    }
                    return null;
                }));
            }
            for (Future<?> future : inviteVerifications) future.get(20, TimeUnit.SECONDS);
            assertThat(inviteAllowed.get()).isEqualTo(2);
            assertThat(inviteRejected.get()).isEqualTo(6);
            Integer inviteUses = jdbc.queryForObject(
                    "select use_count from community_invite_batches where id = '01JINVITE00000000000000000'", Integer.class);
            assertThat(inviteUses).isEqualTo(2);

            MysqlAdvisoryLock advisory = new MysqlAdvisoryLock(dataSource);
            CountDownLatch held = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<Boolean> first = pool.submit(() -> advisory.runExclusive("content-lifecycle", () -> {
                held.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(advisory.runExclusive("content-lifecycle", () -> { throw new IllegalStateException("should not run"); })).isFalse();
            release.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();

            String mediaOwnerId = cn.voidhaven.api.support.Ids.ulid();
            jdbc.update("insert into accounts (id, login_id, password_hash, qualification_status) values (?, 'media.owner', 'x', 'ACTIVE')", mediaOwnerId);
            jdbc.update("insert into media_assets (id, storage_key, media_type, size_bytes, width_px, height_px, owner_account_id, lifecycle_state) values (?, 'content/revision/asset.jpg', 'image/jpeg', 3, 1, 1, ?, 'DELETION_PENDING')", "01JMEDIA00000000000000000", mediaOwnerId);
            jdbc.update("insert into media_deletion_jobs (id, media_asset_id, storage_key, state) values ('01JJOB0000000000000000000', '01JMEDIA00000000000000000', 'content/revision/asset.jpg', 'PENDING')");
            int claimedA = jdbc.update("update media_deletion_jobs set locked_by = 'instance-a', locked_until = date_add(current_timestamp(6), interval 10 minute) where id = '01JJOB0000000000000000000' and (locked_until is null or locked_until < current_timestamp(6))");
            int claimedB = jdbc.update("update media_deletion_jobs set locked_by = 'instance-b', locked_until = date_add(current_timestamp(6), interval 10 minute) where id = '01JJOB0000000000000000000' and (locked_until is null or locked_until < current_timestamp(6))");
            assertThat(claimedA).isEqualTo(1);
            assertThat(claimedB).isZero();
            String owner = jdbc.queryForObject("select locked_by from media_deletion_jobs where id = '01JJOB0000000000000000000'", String.class);
            assertThat(owner).isEqualTo("instance-a");

            CyclicBarrier uniqueStart = new CyclicBarrier(2);
            AtomicInteger uniqueConflict = new AtomicInteger();
            List<Future<?>> unique = new ArrayList<>();
            unique.add(pool.submit(() -> {
                uniqueStart.await();
                try {
                    jdbc.update("insert into accounts (id, login_id, password_hash, qualification_status) values (?, 'same.admin', 'x', 'PENDING')", cn.voidhaven.api.support.Ids.ulid());
                } catch (RuntimeException error) {
                    uniqueConflict.incrementAndGet();
                }
                return null;
            }));
            unique.add(pool.submit(() -> {
                uniqueStart.await();
                try {
                    jdbc.update("insert into accounts (id, login_id, password_hash, qualification_status) values (?, 'same.admin', 'x', 'PENDING')", cn.voidhaven.api.support.Ids.ulid());
                } catch (RuntimeException error) {
                    uniqueConflict.incrementAndGet();
                }
                return null;
            }));
            for (Future<?> future : unique) future.get(20, TimeUnit.SECONDS);
            Integer accounts = jdbc.queryForObject("select count(*) from accounts where login_id = 'same.admin'", Integer.class);
            assertThat(accounts).isEqualTo(1);
            assertThat(uniqueConflict.get()).isEqualTo(1);

            pool.shutdownNow();
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
