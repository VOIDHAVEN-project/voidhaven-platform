package cn.voidhaven.api.web;

import cn.voidhaven.api.service.MediaReadService;
import cn.voidhaven.api.service.MediaStorage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicMediaControllerTest {
    @Test void refuses_to_cache_public_media_so_an_emergency_hide_takes_effect_on_the_next_request() {
        MediaReadService media = mock(MediaReadService.class);
        when(media.loadPublic("01JMEDIA00000000000000000"))
                .thenReturn(new MediaStorage.StoredObject(new byte[] {1, 2}, "image/png"));

        var response = new PublicMediaController(media).read("01JMEDIA00000000000000000");

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
    }
}
