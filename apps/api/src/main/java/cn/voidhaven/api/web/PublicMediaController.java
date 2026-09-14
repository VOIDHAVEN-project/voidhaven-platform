package cn.voidhaven.api.web;

import cn.voidhaven.api.service.MediaReadService;
import cn.voidhaven.api.service.CommunityService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicMediaController {
    private final MediaReadService media;
    private final CommunityService community;

    @Autowired
    public PublicMediaController(MediaReadService media, CommunityService community) { this.media = media; this.community = community; }
    PublicMediaController(MediaReadService media) { this(media, null); }

    @GetMapping("/api/v1/public/media/{id}")
    ResponseEntity<byte[]> read(@PathVariable String id) {
        var stored = media.loadPublic(id);
        // Hiding a content item must take effect on the next request.  Do not
        // permit a browser or shared cache to retain a still-readable image.
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.mediaType())).cacheControl(CacheControl.noStore()).body(stored.bytes());
    }

    @GetMapping("/api/v1/public/community/media/{id}")
    ResponseEntity<byte[]> readCommunity(@PathVariable String id, @org.springframework.web.bind.annotation.RequestHeader("X-Community-Grant") String grant) {
        community.requireGrant(grant);
        var stored = media.loadCommunity(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.mediaType())).cacheControl(CacheControl.noStore()).body(stored.bytes());
    }
}
