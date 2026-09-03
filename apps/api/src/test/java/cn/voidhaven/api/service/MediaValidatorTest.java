package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaValidatorTest {
    @Test void decodes_real_png_pixels_and_reencodes_without_the_input_metadata() throws Exception {
        BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF007A5B);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);

        MediaValidator.ValidatedImage validated = new MediaValidator().validate(bytes.toByteArray());

        assertThat(validated.mediaType()).isEqualTo("image/png");
        assertThat(validated.width()).isEqualTo(2);
        assertThat(validated.height()).isEqualTo(3);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(validated.bytes()))).isNotNull();
    }

    @Test void rejects_non_image_bytes_even_when_a_client_may_claim_an_image_content_type() {
        assertThatThrownBy(() -> new MediaValidator().validate("not an image".getBytes()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("仅支持");
    }
}
