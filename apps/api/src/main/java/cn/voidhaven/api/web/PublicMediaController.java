package cn.voidhaven.api.web;

import cn.voidhaven.api.service.MediaReadService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicMediaController {
    private final MediaReadService media;

    public PublicMediaController(MediaReadService media) { this.media = media; }

    @GetMapping("/api/v1/public/media/{id}")
    ResponseEntity<byte[]> read(@PathVariable String id) {
        var stored = media.loadPublic(id);
        // Hiding a content item must take effect on the next request.  Do not
        // permit a browser or shared cache to retain a still-readable image.
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.mediaType())).cacheControl(CacheControl.noStore()).body(stored.bytes());
    }
}
