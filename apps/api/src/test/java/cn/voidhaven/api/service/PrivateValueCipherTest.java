package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivateValueCipherTest {
    @Test void fails_closed_when_disabled_or_started_without_the_current_key() {
        Environment environment = environment();
        PrivateValueCipher disabled = new PrivateValueCipher(false, "", "", environment);
        assertThatThrownBy(disabled::requireEnabled)
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "PRIVATE_CONTACT_DISABLED");
        assertThatThrownBy(() -> new PrivateValueCipher(true, "k2", "k1:" + key(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current AES-256 key");
    }

    @Test void community_can_use_keys_when_public_contact_collection_is_off() {
        PrivateValueCipher cipher = new PrivateValueCipher(false, "k1", "k1:" + key(), environment());
        assertThatThrownBy(cipher::requireEnabled)
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "PRIVATE_CONTACT_DISABLED");
        PrivateValueCipher.EncryptedValue encrypted = cipher.encrypt("wechat:community", "COMMUNITY_ENTRY:01JENTRY0000000000000000001");
        assertThat(cipher.decrypt(encrypted.ciphertext(), "k1", "COMMUNITY_ENTRY:01JENTRY0000000000000000001")).isEqualTo("wechat:community");
    }

    @Test void encrypts_and_decrypts_with_aad_and_never_returns_plaintext_as_ciphertext() {
        PrivateValueCipher cipher = new PrivateValueCipher(true, "k1", "k1:" + key() + ",k0:" + key(), environment());
        PrivateValueCipher.EncryptedValue encrypted = cipher.encrypt("wechat:example", "SUBMISSION:01JCASE0000000000000000001");
        assertThat(encrypted.keyId()).isEqualTo("k1");
        assertThat(encrypted.ciphertext()).startsWith("v1:").doesNotContain("wechat:example");
        assertThat(cipher.decrypt(encrypted.ciphertext(), "k1", "SUBMISSION:01JCASE0000000000000000001")).isEqualTo("wechat:example");
        assertThatThrownBy(() -> cipher.decrypt(encrypted.ciphertext(), "k1", "other-context"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "PRIVATE_VALUE_DECRYPTION_FAILED");
    }

    private static Environment environment() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        return environment;
    }

    private static String key() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i + 3);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
