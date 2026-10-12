package cn.voidhaven.api.integration;

import cn.voidhaven.api.VoidhavenApiApplication;
import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.support.Ids;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.*;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

/** Synthetic real HTTP -> Spring Session/CSRF -> authorization/locks -> MySQL/private S3 tests. */
class ControlledAccountsHttpIT {
    final JsonMapper json = JsonMapper.builder().build();
    String base;
    JdbcTemplate jdbc;
    HttpClient root, main, member, admin;
    String password = "Synthetic-only-" + UUID.randomUUID();
    @Test void controlled_accounts_authorization_and_organization_members_have_no_revocation_bypass() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        try (var mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("controlled_accounts").withUsername("test").withPassword("test-only");
             var minio = new MinIOContainer(DockerImageName.parse("coollabsio/minio:RELEASE.2025-04-22T22-12-26Z").asCompatibleSubstituteFor("minio/minio"))) {
            Startables.deepStart(Stream.of(mysql, minio)).join();
            try (var s3 = S3Client.builder().endpointOverride(URI.create(minio.getS3URL())).region(Region.US_EAST_1).forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.getUserName(), minio.getPassword()))).build()) {
                s3.createBucket(b -> b.bucket("controlled-tests"));
            }
            try (var app = new SpringApplicationBuilder(VoidhavenApiApplication.class).run(
                    "--spring.profiles.active=local", "--server.port=0", "--server.address=127.0.0.1", "--logging.level.root=WARN",
                    "--spring.datasource.url=" + mysql.getJdbcUrl(), "--spring.datasource.username=" + mysql.getUsername(), "--spring.datasource.password=" + mysql.getPassword(),
                    "--voidhaven.bootstrap-admin-login=controlled-root", "--voidhaven.bootstrap-admin-password=" + password,
                    "--voidhaven.bootstrap-partner-login=controlled-main", "--voidhaven.bootstrap-partner-password=" + password,
                    "--voidhaven.local-fixture-sources-enabled=true", "--voidhaven.private-contact-enabled=true", "--voidhaven.community-gate-enabled=true", "--voidhaven.private-value-current-key-id=synthetic", "--voidhaven.private-value-keys=synthetic:dGVzdC1vbmx5LXByaXZhdGUtdmFsdWUta2V5LTMyISE=",
                    "--voidhaven.cookie-secure=false", "--server.servlet.session.cookie.secure=false", "--voidhaven.public-base-url=http://localhost:8080",
                    "--voidhaven.media-endpoint=" + minio.getS3URL(), "--voidhaven.media-access-key=" + minio.getUserName(), "--voidhaven.media-secret-key=" + minio.getPassword(), "--voidhaven.media-bucket=controlled-tests")) {
                base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort() + "/api/v1";
                jdbc = app.getBean(JdbcTemplate.class);
                root = login("controlled-root", password); main = login("controlled-main", password);
                String rootId = get(root, "/auth/me").path("accountId").asText();
                assertStatus(writeRaw(root, "POST", "/admin/accounts/" + rootId + "/password-reset-links", reason(), null, key()), 403);
                String org = get(main, "/auth/me").path("organization").path("id").asText();
                assertThat(get(main, "/auth/me").path("organization").path("memberQuota").asInt()).isZero();
                assertStatus(writeRaw(main, "POST", "/partner/members", create("blocked-member"), null, key()), 409);

                // Empty legacy/current ADMIN authorizations deny every business route.
                var provisionWithGrants = (tools.jackson.databind.node.ObjectNode) create("granted-at-creation");
                provisionWithGrants.set("authorization", authorization("CONTENT_READ"));
                assertThat(write(root, "POST", "/admin/accounts", provisionWithGrants, null, key()).path("activationToken").asText()).isNotBlank();
                JsonNode createdAdmin = write(root, "POST", "/admin/accounts", create("controlled-admin"), null, key());
                String adminId = createdAdmin.path("id").asText();
                activate(createdAdmin.path("activationToken").asText()); admin = login("controlled-admin", password);
                assertStatus(send(admin, "GET", "/admin/content-items", null, null, null, null), 403);
                JsonNode grants = authorization("CONTENT_EDIT", "CONTENT_REVIEW", "CONTENT_PUBLISH");
                JsonNode preview = write(root, "POST", "/admin/accounts/" + adminId + "/authorization/preview", grants, null, null);
                JsonNode saved = write(root, "PUT", "/admin/accounts/" + adminId + "/authorization", grants, "0", key());
                assertThat(saved).isEqualTo(preview.path("proposed"));
                assertStatus(send(admin, "GET", "/auth/me", null, null, null, null), 401);
                admin = login("controlled-admin", password);
                String competition = jdbc.queryForObject("select id from content_items where kind = 'COMPETITION' limit 1", String.class);
                assertStatus(send(admin, "GET", "/admin/content-items/" + competition, null, null, null, null), 403);
                String replayKey = key();
                JsonNode own = write(admin, "POST", "/admin/content-items", draft("限定栏目草稿", false), null, replayKey);
                assertThat(write(admin, "POST", "/admin/content-items", draft("限定栏目草稿", false), null, replayKey)).isEqualTo(own);
                JsonNode rootOwn = write(root, "POST", "/admin/content-items", draft("限定栏目草稿", false), null, replayKey);
                assertThat(rootOwn.path("id").asText()).isNotEqualTo(own.path("id").asText());
                String item = own.path("id").asText();
                write(admin, "POST", "/admin/content-items/" + item + "/submit-review", json.createObjectNode(), null, key());
                write(admin, "POST", "/admin/reviews/" + item + "/decision", json.readTree("{\"decision\":\"APPROVE\"}"), get(admin, "/admin/content-items/" + item).path("revision").asText(), key());
                write(admin, "POST", "/admin/content-items/" + item + "/publish", json.createObjectNode(), null, key());
                assertThat(get(client(), "/public/content/" + item).path("title").asText()).isEqualTo("限定栏目草稿");
                assertStatus(writeRaw(root, "PUT", "/admin/accounts/" + adminId + "/authorization", grants, "0", key()), 409);
                write(root, "POST", "/admin/accounts/" + adminId + "/authorization/revoke", reason(), "1", key());
                assertStatus(writeRaw(admin, "POST", "/admin/content-items", draft("限定栏目草稿", false), null, replayKey), 401);
                admin = login("controlled-admin", password);
                assertStatus(send(admin, "GET", "/admin/content-items", null, null, null, null), 403);
                write(root, "PUT", "/admin/accounts/" + adminId + "/authorization", grants, "2", key());
                admin = login("controlled-admin", password);
                jdbc.update("update admin_authorizations set long_term = false, expires_at = date_sub(current_timestamp(6), interval 1 second) where account_id = ?", adminId);
                assertStatus(send(admin, "GET", "/admin/content-items", null, null, null, null), 403);
                assertThat(get(root, "/admin/accounts/" + adminId + "/authorization/history").size()).isEqualTo(3);

                // The organization lock makes competing final-slot creates mutually exclusive.
                write(root, "PATCH", "/admin/partner-profiles/" + org + "/member-quota", json.createObjectNode().put("quota", 1).put("reason", "synthetic cap"), "0", key());
                var pool = Executors.newFixedThreadPool(2);
                List<HttpResponse<String>> race;
                try {
                    var first = pool.submit(() -> writeRaw(main, "POST", "/partner/members", create("member-first"), null, key()));
                    var second = pool.submit(() -> writeRaw(main, "POST", "/partner/members", create("member-second"), null, key()));
                    race = List.of(first.get(40, TimeUnit.SECONDS), second.get(40, TimeUnit.SECONDS));
                } finally { pool.shutdownNow(); }
                assertThat(race.stream().map(HttpResponse::statusCode).sorted().toList()).containsExactly(200, 409);
                JsonNode createdMember = response(race.stream().filter(r -> r.statusCode() == 200).findFirst().orElseThrow());
                String memberId = createdMember.path("id").asText(), memberLogin = createdMember.path("loginId").asText();
                JsonNode issued = write(main, "POST", "/partner/members/" + memberId + "/activation-links", reason(), null, key());
                activate(issued.path("activationToken").asText()); member = login(memberLogin, password);
                assertThat(get(member, "/auth/me").path("organization").path("accountKind").asText()).isEqualTo("MEMBER");
                assertStatus(send(member, "GET", "/partner/members", null, null, null, null), 403);
                assertStatus(writeRaw(member, "POST", "/admin/content-items/" + item + "/publish", json.createObjectNode(), null, key()), 404);
                assertStatus(writeRaw(main, "POST", "/partner/members/" + get(main, "/auth/me").path("accountId").asText() + "/password-reset-links", reason(), null, key()), 404);
                JsonNode shared = write(member, "POST", "/partner/content-items", draft("组织共享草稿", true), null, key());
                String sharedId = shared.path("id").asText();
                assertStatus(writeRaw(member, "POST", "/admin/content-items/" + sharedId + "/publish", json.createObjectNode(), null, key()), 403);
                assertStatus(writeRaw(member, "POST", "/admin/reviews/" + sharedId + "/decision", json.readTree("{\"decision\":\"APPROVE\"}"), "0", key()), 403);
                JsonNode editable = get(main, "/partner/content-items/" + sharedId);
                String version = editable.path("revision").asText();
                write(main, "PATCH", "/partner/content-items/" + sharedId, draft("主账号编辑共享稿", true), version, key());
                assertStatus(writeRaw(member, "PATCH", "/partner/content-items/" + sharedId, draft("冲突编辑", true), version, key()), 409);

                // Bound media are shared within the organization, even when another member uploaded them.
                JsonNode image = upload(main, sharedId);
                String asset = image.path("id").asText();
                assertStatus(send(member, "GET", "/media/" + asset + "/preview", null, null, null, null), 200);
                JsonNode qrImage = upload(main, sharedId, true);
                String privateQr = qrImage.path("id").asText();
                assertStatus(send(member, "GET", "/media/" + privateQr + "/preview", null, null, null, null), 404);
                JsonNode gated = json.readTree("{\"actions\":[{\"presentationType\":\"TEXT\",\"contactChannel\":\"WECHAT\",\"accessLevel\":\"COMMUNITY_GATED\",\"label\":\"合成联系方式\",\"value\":\"synthetic-private-only\",\"publicExposureConfirmed\":false,\"enabled\":true,\"sortOrder\":0}]}" );
                ((tools.jackson.databind.node.ArrayNode) gated.path("actions")).addObject()
                    .put("presentationType", "QR_CODE").put("contactChannel", "WECHAT").put("accessLevel", "COMMUNITY_GATED")
                    .put("label", "合成私密二维码").put("mediaAssetId", privateQr).put("publicExposureConfirmed", false).put("enabled", true).put("sortOrder", 1);
                write(main, "PUT", "/partner/content-items/" + sharedId + "/actions", gated, get(main, "/partner/content-items/" + sharedId).path("revision").asText(), key());
                JsonNode readRecruitment = authorization("CONTENT_EDIT");
                ((tools.jackson.databind.node.ObjectNode) readRecruitment.path("grants").get(0)).put("scopeType", "GLOBAL").set("scopeIds", json.createArrayNode());
                write(root, "PUT", "/admin/accounts/" + adminId + "/authorization", readRecruitment, "3", key()); admin = login("controlled-admin", password);
                assertThat(get(admin, "/admin/content-items/" + sharedId + "/actions").get(0).path("value").asText("")).isEmpty();
                assertStatus(send(admin, "GET", "/media/" + privateQr + "/preview", null, null, null, null), 404);
                assertStatus(send(member, "GET", "/media/" + privateQr + "/preview", null, null, null, null), 200);
                assertStatus(writeRaw(admin, "PUT", "/admin/content-items/" + sharedId + "/actions", json.readTree("{\"actions\":[]}"), get(admin, "/admin/content-items/" + sharedId).path("revision").asText(), key()), 403);

                // Synthetic second organization; no contacts or business records are copied from outside this container.
                app.getBean(TransactionTemplate.class).executeWithoutResult(tx -> {
                    var account = new Account(Ids.ulid(), "other-main", app.getBean(PasswordEncoder.class).encode(password));
                    account.addRole(app.getBean(RoleRepository.class).findByCode("PARTNER").orElseThrow());
                    app.getBean(AccountRepository.class).saveAndFlush(account);
                    app.getBean(PartnerProfileRepository.class).saveAndFlush(new PartnerProfile(Ids.ulid(), account.getId(), "第二合成组织", 2));
                });
                HttpClient other = login("other-main", password);
                assertStatus(send(other, "GET", "/partner/content-items/" + sharedId, null, null, null, null), 404);
                assertStatus(send(other, "GET", "/media/" + asset + "/preview", null, null, null, null), 404);
                assertStatus(writeRaw(other, "POST", "/partner/members/" + memberId + "/suspend", reason(), null, key()), 404);

                JsonNode oldReset = write(main, "POST", "/partner/members/" + memberId + "/password-reset-links", reason(), null, key());
                // A login validated against the previous password cannot keep a usable session after the concurrent change.
                HttpClient concurrentLogin = client();
                var loginRace = Executors.newFixedThreadPool(2);
                var loginBarrier = new CyclicBarrier(2);
                try {
                    var loginResult = loginRace.submit(() -> { loginBarrier.await(); return send(concurrentLogin, "POST", "/auth/session", json.createObjectNode().put("loginId", memberLogin).put("password", password), null, null, null); });
                    var changeResult = loginRace.submit(() -> { loginBarrier.await(); return writeRaw(member, "POST", "/auth/password/change", json.createObjectNode().put("currentPassword", password).put("newPassword", password + "changed"), null, null); });
                    assertStatus(changeResult.get(40, TimeUnit.SECONDS), 204);
                    assertThat(loginResult.get(40, TimeUnit.SECONDS).statusCode()).isIn(200, 401);
                    assertStatus(send(concurrentLogin, "GET", "/auth/me", null, null, null, null), 401);
                } finally { loginRace.shutdownNow(); }
                assertStatus(reset(oldReset.path("resetToken").asText(), password + "recovered"), 400);
                member = login(memberLogin, password + "changed");
                JsonNode reset = write(main, "POST", "/partner/members/" + memberId + "/password-reset-links", reason(), null, key());
                var consumePool = Executors.newFixedThreadPool(2);
                try {
                    var first = consumePool.submit(() -> reset(reset.path("resetToken").asText(), password + "recovered"));
                    var second = consumePool.submit(() -> reset(reset.path("resetToken").asText(), password + "recovered"));
                    assertThat(Stream.of(first.get(40, TimeUnit.SECONDS), second.get(40, TimeUnit.SECONDS)).map(HttpResponse::statusCode).sorted().toList()).containsExactly(200, 400);
                } finally { consumePool.shutdownNow(); }
                member = login(memberLogin, password + "recovered");
                write(main, "POST", "/partner/members/" + memberId + "/suspend", reason(), null, key());
                assertThat(get(main, "/partner/members").path("organization").path("memberCount").asInt()).isEqualTo(1);
                write(main, "POST", "/partner/members/" + memberId + "/reactivate", reason(), null, key());
                assertStatus(send(member, "GET", "/auth/me", null, null, null, null), 401);
                member = login(memberLogin, password + "recovered");
                JsonNode cap = write(root, "PATCH", "/admin/partner-profiles/" + org + "/member-quota", json.createObjectNode().put("quota", 0).put("reason", "synthetic decrease"), "1", key());
                assertThat(get(member, "/partner/dashboard").path("organization").path("memberCount").asInt()).isEqualTo(1);
                assertStatus(writeRaw(main, "POST", "/partner/members", create("blocked-lowered"), null, key()), 409);
                JsonNode increased = write(root, "PATCH", "/admin/partner-profiles/" + org + "/member-quota", json.createObjectNode().put("quota", 2).put("reason", "pending member revocation test"), cap.path("version").asText(), key());
                JsonNode pending = write(main, "POST", "/partner/members", create("pending-during-stop"), null, key());
                String pendingId = pending.path("id").asText();
                write(main, "POST", "/partner/members/" + pendingId + "/suspend", reason(), null, key());
                assertThat(write(main, "POST", "/partner/members/" + pendingId + "/reactivate", reason(), null, key()).path("qualificationStatus").asText()).isEqualTo("PENDING");
                JsonNode pendingLink = write(main, "POST", "/partner/members/" + pendingId + "/activation-links", reason(), null, key());
                JsonNode resetBeforeStop = write(main, "POST", "/partner/members/" + memberId + "/password-reset-links", reason(), null, key());
                // Publish one organization-owned resource, then show suspension never implicitly retracts it.
                write(root, "POST", "/admin/content-ownership-transfers", json.createObjectNode().put("contentItemId", item).put("toPartnerProfileId", org).put("reason", "synthetic transfer"), null, key());
                // A racing content write either finishes while the organization is active, or is denied after suspension.
                JsonNode stopped;
                var stopRace = Executors.newFixedThreadPool(2); var stopBarrier = new CyclicBarrier(2);
                try {
                    var createResult = stopRace.submit(() -> { stopBarrier.await(); return writeRaw(member, "POST", "/partner/content-items", draft("并发停用写入", true), null, key()); });
                    var stopResult = stopRace.submit(() -> { stopBarrier.await(); return write(root, "POST", "/admin/partner-profiles/" + org + "/suspend", reason(), increased.path("version").asText(), key()); });
                    stopped = stopResult.get(40, TimeUnit.SECONDS);
                    assertThat(createResult.get(40, TimeUnit.SECONDS).statusCode()).isIn(200, 401);
                    assertStatus(writeRaw(member, "POST", "/partner/content-items", draft("停用后禁止写入", true), null, key()), 401);
                } finally { stopRace.shutdownNow(); }
                assertStatus(send(main, "GET", "/partner/dashboard", null, null, null, null), 401);
                assertStatus(send(member, "GET", "/auth/me", null, null, null, null), 401);
                assertStatus(reset(resetBeforeStop.path("resetToken").asText(), password + "after-stop"), 400);
                assertStatus(send(client(), "POST", "/auth/activation/consume", json.createObjectNode().put("token", pendingLink.path("activationToken").asText()).put("password", password), null, null, null), 400);
                assertThat(get(client(), "/public/content/" + item).path("title").asText()).isEqualTo("限定栏目草稿");
                write(root, "POST", "/admin/partner-profiles/" + org + "/reactivate", reason(), stopped.path("version").asText(), key());
                assertStatus(reset(resetBeforeStop.path("resetToken").asText(), password + "after-stop"), 400);
                assertStatus(send(client(), "POST", "/auth/activation/consume", json.createObjectNode().put("token", pendingLink.path("activationToken").asText()).put("password", password), null, null, null), 400);
                main = login("controlled-main", password); member = login(memberLogin, password + "recovered");
                write(main, "POST", "/partner/members/" + pendingId + "/revoke", reason(), null, key());
                write(main, "POST", "/partner/members/" + memberId + "/revoke", reason(), null, key());
                assertThat(get(main, "/partner/members").path("organization").path("memberCount").asInt()).isZero();
                assertStatus(writeRaw(main, "POST", "/partner/members/" + memberId + "/reactivate", reason(), null, key()), 409);
                assertThat(get(main, "/partner/content-items/" + sharedId).path("ownerPartnerProfileId").asText()).isEqualTo(org);
                assertThat(jdbc.queryForObject("select count(*) from idempotency_records where cast(response_body as char) like '%" + resetBeforeStop.path("resetToken").asText() + "%'", Integer.class)).isZero();
            }
        }
    }
    String key() { return UUID.randomUUID().toString(); }
    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build(); }
    HttpClient login(String login, String password) throws Exception { var c = client(); response(send(c, "POST", "/auth/session", json.createObjectNode().put("loginId", login).put("password", password), null, null, null)); return c; }
    JsonNode create(String login) { return json.createObjectNode().put("loginId", login).put("reason", "synthetic-only test"); }
    JsonNode reason() { return json.createObjectNode().put("reason", "synthetic-only test"); }
    void activate(String token) throws Exception { response(send(client(), "POST", "/auth/activation/consume", json.createObjectNode().put("token", token).put("password", password), null, null, null)); }
    HttpResponse<String> reset(String token, String password) throws Exception { return send(client(), "POST", "/auth/password-reset/consume", json.createObjectNode().put("token", token).put("newPassword", password), null, null, null); }
    JsonNode authorization(String... permissions) {
        var body = json.createObjectNode().put("longTerm", true).put("reason", "synthetic-only authorization"); var grants = body.putArray("grants");
        for (String permission : permissions) grants.addObject().put("permission", permission).put("scopeType", "MODULES").putArray("scopeIds").add("resource");
        return body;
    }
    JsonNode draft(String title, boolean recruitment) {
        var body = json.createObjectNode().put("kind", recruitment ? "RECRUITMENT" : "RESOURCE").put("moduleKey", recruitment ? "recruitment" : "resource")
                .put("sourceUrl", "http://localhost:8080/local-fixtures/source").put("sourceName", "本地合成来源")
                .put("schemaVersion", 2).put("resourceConfigVersion", 1).put("title", title).put("summary", "用于受控账号验收的合成内容")
                .put("markdownSource", "# 合成正文\n\n用于隔离测试。").put("languageCode", "zh-CN").put("rightsBasis", "合成验收数据")
                .put("authorDisplayName", "合成作者").put("reprintPermission", "NOT_ALLOWED").put("adaptationPermission", "NOT_ALLOWED");
        body.set("body", json.readTree("{\"version\":1,\"blocks\":[]}"));
        body.set("details", recruitment ? json.readTree("{\"deadlineDate\":\"2030-10-01\"}") : json.readTree("{\"sourceMode\":\"ORIGINAL\",\"resourceType\":\"GENERAL\"}"));
        return body;
    }
    JsonNode upload(HttpClient c, String contentId) throws Exception { return upload(c, contentId, false); }
    JsonNode upload(HttpClient c, String contentId, boolean action) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB); var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes);
        String boundary = "isolated-" + key(); var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"contentItemId\"\r\n\r\n" + contentId + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"altText\"\r\n\r\nSynthetic image\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"synthetic.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write(bytes.toByteArray()); body.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode csrf = get(c, "/auth/csrf");
        return response(c.send(HttpRequest.newBuilder(URI.create(base + (action ? "/media/action-uploads" : "/media/uploads"))).timeout(Duration.ofSeconds(30)).header("Content-Type", "multipart/form-data; boundary=" + boundary).header("Idempotency-Key", key()).header(csrf.path("headerName").asText(), csrf.path("token").asText()).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString()));
    }
    JsonNode get(HttpClient c, String path) throws Exception { return response(send(c, "GET", path, null, null, null, null)); }
    JsonNode write(HttpClient c, String method, String path, JsonNode body, String version, String key) throws Exception { return response(writeRaw(c, method, path, body, version, key)); }
    HttpResponse<String> writeRaw(HttpClient c, String method, String path, JsonNode body, String version, String key) throws Exception { return send(c, method, path, body, version, key, get(c, "/auth/csrf")); }
    HttpResponse<String> send(HttpClient c, String method, String path, JsonNode body, String version, String key, JsonNode csrf) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
        if (version != null) req.header("If-Match", version); if (key != null) req.header("Idempotency-Key", key); if (csrf != null) req.header(csrf.path("headerName").asText(), csrf.path("token").asText());
        return c.send(req.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    void assertStatus(HttpResponse<String> response, int status) { assertThat(response.statusCode()).as("%s: %s", response.uri(), response.body()).isEqualTo(status); }
    JsonNode response(HttpResponse<String> response) { assertThat(response.statusCode()).as("%s: %s", response.uri(), response.body()).isBetween(200, 299); return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body()).path("data"); }
}
