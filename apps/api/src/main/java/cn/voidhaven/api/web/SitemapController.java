package cn.voidhaven.api.web;

import cn.voidhaven.api.service.ContentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;

/**
 * Serves /api/v1/public/sitemap.xml so published content is actually discoverable by
 * search engines. The route lives under the already-proxied /api/ prefix specifically so
 * no nginx changes are required to expose it at the edge; robots.txt links to it directly.
 */
@RestController
public class SitemapController {
    private final ContentService content;

    @Value("${voidhaven.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public SitemapController(ContentService content) {
        this.content = content;
    }

    @GetMapping(value = "/api/v1/public/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<String> sitemap() {
        String base = publicBaseUrl.replaceAll("/$", "");
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        xml.append("  <url><loc>").append(xml(base + "/")).append("</loc></url>\n");
        for (ContentService.SitemapEntry entry : content.publicSitemapEntries()) {
            xml.append("  <url><loc>").append(xml(base + "/content/" + entry.id())).append("</loc></url>\n");
        }
        xml.append("</urlset>\n");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(15))).body(xml.toString());
    }

    private String xml(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
