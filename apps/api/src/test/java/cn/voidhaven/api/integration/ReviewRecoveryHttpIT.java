package cn.voidhaven.api.integration;

import cn.voidhaven.api.VoidhavenApiApplication;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class ReviewRecoveryHttpIT {
    final JsonMapper json = JsonMapper.builder().build();
    String base;
    JdbcTemplate jdbc;
    HttpClient root;
    String password = "Synthetic-only-" + UUID.randomUUID();

    @Test void approved_recruitment_recovers_through_real_http_and_mysql() throws Exception {
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { docker = false; }
        if ("true".equalsIgnoreCase(System.getenv("CI"))) assertThat(docker).as("CI must execute the HTTP/MySQL integration test").isTrue();
        Assumptions.assumeTrue(docker, "Docker unavailable; HTTP/MySQL integration was not executed");
        try (var mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("review_recovery").withUsername("test").withPassword("test-only")) {
            mysql.start();
            try (var app = new SpringApplicationBuilder(VoidhavenApiApplication.class).run(
                    "--spring.profiles.active=local", "--server.port=0", "--server.address=127.0.0.1", "--logging.level.root=WARN",
                    "--spring.datasource.url=" + mysql.getJdbcUrl(), "--spring.datasource.username=" + mysql.getUsername(), "--spring.datasource.password=" + mysql.getPassword(),
                    "--voidhaven.bootstrap-admin-login=recovery-root", "--voidhaven.bootstrap-admin-password=" + password,
                    "--voidhaven.bootstrap-partner-login=recovery-partner", "--voidhaven.bootstrap-partner-password=" + password,
                    "--voidhaven.cookie-secure=false", "--server.servlet.session.cookie.secure=false", "--voidhaven.public-base-url=http://localhost:8080")) {
                base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort() + "/api/v1";
                jdbc = app.getBean(JdbcTemplate.class);
                root = login("recovery-root", password);
                verifyApprovedRecruitmentRecovery();
            }
        }
    }
    void verifyApprovedRecruitmentRecovery() throws Exception {
        String item = write(root, "POST", "/admin/content-items", draft("合成审核恢复招募", true), null, key()).path("id").asText();
        String itemPath = "/admin/content-items/" + item;
        String decisionPath = "/admin/reviews/" + item + "/decision";
        HttpResponse<String> missingAction = writeRaw(root, "POST", itemPath + "/submit-review", json.createObjectNode(), null, key());
        assertStatus(missingAction, 409);
        assertThat(json.readTree(missingAction.body()).path("code").asText()).isEqualTo("RECRUITMENT_ACTION_REQUIRED");
        assertThat(get(root, itemPath).path("workflowStatus").asText()).isEqualTo("DRAFT");

        // Only in the disposable container: represent a legacy approved item lacking actions.
        jdbc.update("update content_items set workflow_status = 'APPROVED' where id = ?", item);
        JsonNode approved = get(root, itemPath);
        String version = approved.path("revision").asText();
        JsonNode returning = json.createObjectNode().put("decision", "CHANGES_REQUESTED").put("reason", "补齐合成行动入口");
        HttpClient partner = login("recovery-partner", password);
        HttpResponse<String> denied = writeRaw(partner, "POST", decisionPath, returning, version, key());
        assertStatus(denied, 404);
        assertThat(json.readTree(denied.body()).path("code").asText()).isEqualTo("CONTENT_NOT_FOUND");
        assertStatus(send(root, "POST", decisionPath, returning, version, key(), null), 403);
        assertThat(get(root, itemPath + "/publication-readiness").path("ready").asBoolean()).isFalse();
        assertStatus(writeRaw(root, "POST", decisionPath, returning, String.valueOf(Long.parseLong(version) + 1), key()), 409);
        assertStatus(writeRaw(root, "POST", decisionPath, json.createObjectNode().put("decision", "CHANGES_REQUESTED").put("reason", " "), version, key()), 400);
        assertStatus(writeRaw(root, "POST", decisionPath, json.createObjectNode().put("decision", "REJECT").put("reason", "不能直接拒绝已通过稿件"), version, key()), 409);
        String returnKey = key();
        JsonNode returned = write(root, "POST", decisionPath, returning, version, returnKey);
        assertThat(returned.path("workflowStatus").asText()).isEqualTo("CHANGES_REQUESTED");
        assertThat(write(root, "POST", decisionPath, returning, version, returnKey)).isEqualTo(returned);
        assertThat(get(root, itemPath).path("reviews").toString()).contains("CHANGES_REQUESTED", "补齐合成行动入口");

        JsonNode actions = json.readTree("{\"actions\":[{\"presentationType\":\"LINK\",\"contactChannel\":\"URL\",\"accessLevel\":\"PUBLIC\",\"label\":\"合成报名入口\",\"value\":\"https://example.org/apply\",\"enabled\":true,\"publicExposureConfirmed\":false,\"sortOrder\":0}]}");
        write(root, "PUT", itemPath + "/actions", actions, get(root, itemPath).path("revision").asText(), key());
        write(root, "POST", itemPath + "/submit-review", json.createObjectNode(), null, key());
        write(root, "POST", decisionPath, json.createObjectNode().put("decision", "APPROVE"), get(root, itemPath).path("revision").asText(), key());
        assertThat(get(root, itemPath + "/publication-readiness").path("ready").asBoolean()).isTrue();
        write(root, "POST", itemPath + "/publish", json.createObjectNode(), null, key());
        assertThat(get(client(), "/public/content/" + item).path("title").asText()).isEqualTo("合成审核恢复招募");
    }

    String key() { return UUID.randomUUID().toString(); }
    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build(); }
    HttpClient login(String login, String password) throws Exception { var c = client(); response(send(c, "POST", "/auth/session", json.createObjectNode().put("loginId", login).put("password", password), null, null, null)); return c; }
    JsonNode draft(String title, boolean recruitment) throws Exception {
        var body = json.createObjectNode().put("kind", recruitment ? "RECRUITMENT" : "RESOURCE").put("moduleKey", recruitment ? "recruitment" : "resource")
                .put("sourceUrl", "http://localhost:8080/local-fixtures/source").put("sourceName", "本地合成来源")
                .put("schemaVersion", get(root, "/content-modules").path("version").asInt()).put("title", title).put("summary", "用于审核恢复验收的合成内容")
                .put("markdownSource", "# 合成正文\n\n用于隔离测试。").put("languageCode", "zh-CN").put("rightsBasis", "合成验收数据")
                .put("authorDisplayName", "合成作者").put("reprintPermission", "NOT_ALLOWED").put("adaptationPermission", "NOT_ALLOWED");
        body.set("body", json.readTree("{\"version\":1,\"blocks\":[]}"));
        body.set("details", recruitment ? json.readTree("{\"deadlineDate\":\"2030-10-01\"}") : json.readTree("{\"sourceMode\":\"ORIGINAL\",\"resourceType\":\"GENERAL\"}"));
        return body;
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
