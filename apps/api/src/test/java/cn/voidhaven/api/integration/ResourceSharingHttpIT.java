package cn.voidhaven.api.integration;

import cn.voidhaven.api.VoidhavenApiApplication;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP/session/CSRF -> application -> isolated MySQL -> public response. */
class ResourceSharingHttpIT {
    final JsonMapper json = JsonMapper.builder().build();
    HttpClient client;
    String base;
    @Test void configuration_to_published_resource_preserves_history_and_enforces_permissions() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(), "Docker 不可用，真实 HTTP 资源闭环未执行。");
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4").withDatabaseName("resource_http_test").withUsername("test").withPassword("test-only-password")) {
            mysql.start();
            String password = "Isolated-only-" + UUID.randomUUID();
            try (var app = new SpringApplicationBuilder(VoidhavenApiApplication.class).run(
                    "--spring.profiles.active=local", "--server.port=0", "--server.address=127.0.0.1", "--logging.level.root=WARN",
                    "--spring.datasource.url=" + mysql.getJdbcUrl(), "--spring.datasource.username=" + mysql.getUsername(), "--spring.datasource.password=" + mysql.getPassword(),
                    "--voidhaven.bootstrap-admin-login=resource-admin", "--voidhaven.bootstrap-admin-password=" + password,
                    "--voidhaven.bootstrap-partner-login=resource-partner", "--voidhaven.bootstrap-partner-password=" + password,
                    "--voidhaven.cookie-secure=false", "--server.servlet.session.cookie.secure=false", "--voidhaven.public-base-url=http://localhost:8080",
                    "--voidhaven.media-endpoint=http://127.0.0.1:9", "--voidhaven.media-access-key=isolated-test", "--voidhaven.media-secret-key=isolated-test-only", "--voidhaven.media-bucket=isolated-test")) {
                base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort() + "/api/v1";
                client = client();
                login("resource-admin", password);
                ObjectNode config = (ObjectNode) get("/admin/resource-config");
                ((tools.jackson.databind.node.ArrayNode) config.path("categories")).add(json.createObjectNode().put("key", "LAB_NOTE").put("label", "实验笔记").put("enabled", true));
                String key = UUID.randomUUID().toString();
                JsonNode updated = write("PUT", "/admin/resource-config", config, "1", key);
                assertThat(updated.path("version").asLong()).isEqualTo(2);
                assertThat(write("PUT", "/admin/resource-config", config, "1", key)).isEqualTo(updated);

                ObjectNode draft = json.createObjectNode();
                draft.put("kind", "RESOURCE").put("moduleKey", "resource").put("schemaVersion", 2).put("resourceConfigVersion", 2)
                        .put("title", "隔离活动笔记").put("summary", "真实接口的合成测试内容").put("markdownSource", "# 合成正文\n\n测试内容。")
                        .put("languageCode", "zh-CN").put("rightsBasis", "原创").put("authorDisplayName", "隔离作者")
                        .put("reprintPermission", "NOT_ALLOWED").put("adaptationPermission", "NOT_ALLOWED");
                draft.set("body", json.readTree("{\"version\":1,\"blocks\":[]}"));
                draft.set("details", json.readTree("{\"resourceType\":\"LAB_NOTE\",\"sourceMode\":\"ORIGINAL\",\"customFields\":{\"eventDate\":\"2030-10-01\"}}"));
                JsonNode created = write("POST", "/admin/content-items", draft, null, UUID.randomUUID().toString());
                String id = created.path("id").asText();
                write("POST", "/admin/content-items/" + id + "/submit-review", json.createObjectNode(), null, UUID.randomUUID().toString());
                JsonNode editable = get("/admin/content-items/" + id);
                write("POST", "/admin/reviews/" + id + "/decision", json.readTree("{\"decision\":\"APPROVE\"}"), editable.path("revision").asText(), UUID.randomUUID().toString());
                write("POST", "/admin/content-items/" + id + "/publish", json.createObjectNode(), null, UUID.randomUUID().toString());
                JsonNode published = get("/public/content/" + id);
                assertThat(published.path("resourcePresentation").path("category").asText()).isEqualTo("实验笔记");
                assertThat(published.path("resourcePresentation").path("fields").get(0).path("label").asText()).isEqualTo("活动日期");

                ObjectNode next = (ObjectNode) updated.deepCopy();
                ((ObjectNode) next.path("fields").get(0)).put("label", "新的日期名称").put("enabled", false);
                write("PUT", "/admin/resource-config", next, "2", UUID.randomUUID().toString());
                assertThat(get("/public/content/" + id).path("resourcePresentation")).isEqualTo(published.path("resourcePresentation"));
                editable = get("/admin/content-items/" + id);
                assertThat(editable.path("resourceConfig").path("fields").get(0).path("label").asText()).isEqualTo("活动日期");
                draft.put("resourceConfigVersion", 3);
                write("PATCH", "/admin/content-items/" + id, draft, editable.path("revision").asText(), UUID.randomUUID().toString());
                assertThat(get("/public/content/" + id).path("resourcePresentation")).isEqualTo(published.path("resourcePresentation"));

                client = client(); login("resource-partner", password);
                assertThat(send("GET", "/admin/resource-config", null, null, null, null).statusCode()).isEqualTo(403);
            }
        }
    }
    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build(); }
    void login(String login, String password) throws Exception {
        var body = json.createObjectNode().put("loginId", login).put("password", password);
        response(send("POST", "/auth/session", body, null, null, null));
    }
    JsonNode get(String path) throws Exception { return response(send("GET", path, null, null, null, null)); }
    JsonNode write(String method, String path, JsonNode body, String version, String key) throws Exception {
        return response(send(method, path, body, version, key, get("/auth/csrf")));
    }
    HttpResponse<String> send(String method, String path, JsonNode body, String version, String key, JsonNode csrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (version != null) request.header("If-Match", version);
        if (key != null) request.header("Idempotency-Key", key);
        if (csrf != null) request.header(csrf.path("headerName").asText(), csrf.path("token").asText());
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode response(HttpResponse<String> response) {
        assertThat(response.statusCode()).as("HTTP %s: %s", response.uri(), response.body()).isBetween(200, 299);
        return json.readTree(response.body()).path("data");
    }
    boolean dockerAvailable() { try { return DockerClientFactory.instance().isDockerAvailable(); } catch (Exception e) { return false; } }
}
