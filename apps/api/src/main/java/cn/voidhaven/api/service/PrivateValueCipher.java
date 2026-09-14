package cn.voidhaven.api.service;

import cn.voidhaven.api.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PrivateValueCipher {
    public record EncryptedValue(String ciphertext, String keyId) { }

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final boolean enabled;
    private final String currentKeyId;
    private final Map<String, SecretKeySpec> keys;
    private final SecureRandom random = new SecureRandom();

    public PrivateValueCipher(
            @Value("${voidhaven.private-contact-enabled:false}") boolean enabled,
            @Value("${voidhaven.private-value-current-key-id:}") String currentKeyId,
            @Value("${voidhaven.private-value-keys:}") String encodedKeys,
            Environment environment) {
        this.enabled = enabled;
        this.currentKeyId = currentKeyId == null ? "" : currentKeyId.trim();
        this.keys = parseKeys(encodedKeys);
        if (enabled && (this.currentKeyId.isBlank() || !this.keys.containsKey(this.currentKeyId))) {
            throw new IllegalStateException("Private contact collection is enabled without a valid current AES-256 key");
        }
        if (isProduction(environment) && enabled && this.currentKeyId.toLowerCase().contains("local")) {
            throw new IllegalStateException("A local private-value key cannot be used in production");
        }
    }

    public boolean isEnabled() { return enabled; }

    public boolean isContactCollectionOperational() {
        return enabled && !currentKeyId.isBlank() && keys.containsKey(currentKeyId);
    }

    public boolean isKeyRingOperational() {
        return !currentKeyId.isBlank() && keys.containsKey(currentKeyId);
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRIVATE_CONTACT_DISABLED", "私密联系人功能尚未安全配置，请稍后再试。");
        }
        requireKeys();
    }

    public void requireKeys() {
        if (currentKeyId.isBlank() || !keys.containsKey(currentKeyId)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRIVATE_CONTACT_DISABLED", "私密字段密钥尚未安全配置，请稍后再试。");
        }
    }

    public EncryptedValue encrypt(String plaintext, String context) {
        requireKeys();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentKeyId), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, packed, 0, nonce.length);
            System.arraycopy(encrypted, 0, packed, nonce.length, encrypted.length);
            return new EncryptedValue("v1:" + Base64.getEncoder().encodeToString(packed), currentKeyId);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to encrypt private value", error);
        }
    }

    public String decrypt(String ciphertext, String keyId, String context) {
        requireKeys();
        SecretKeySpec key = keys.get(keyId);
        if (key == null || ciphertext == null || !ciphertext.startsWith("v1:")) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRIVATE_VALUE_KEY_UNAVAILABLE", "私密信息当前无法安全读取。");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(ciphertext.substring(3));
            if (packed.length <= NONCE_BYTES) throw new IllegalArgumentException("Ciphertext is incomplete");
            byte[] nonce = java.util.Arrays.copyOfRange(packed, 0, NONCE_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, NONCE_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRIVATE_VALUE_DECRYPTION_FAILED", "私密信息当前无法安全读取。");
        }
    }


    private Map<String, SecretKeySpec> parseKeys(String encodedKeys) {
        Map<String, SecretKeySpec> values = new LinkedHashMap<>();
        if (encodedKeys == null || encodedKeys.isBlank()) return values;
        for (String entry : encodedKeys.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2 || parts[0].isBlank()) throw new IllegalStateException("Invalid private-value key ring entry");
            byte[] decoded;
            try { decoded = Base64.getDecoder().decode(parts[1]); }
            catch (IllegalArgumentException error) { throw new IllegalStateException("Invalid private-value key encoding", error); }
            if (decoded.length != 32) throw new IllegalStateException("Private-value keys must be 256 bits");
            values.put(parts[0], new SecretKeySpec(decoded, "AES"));
        }
        return Map.copyOf(values);
    }

    private boolean isProduction(Environment environment) {
        for (String profile : environment.getActiveProfiles()) if ("production".equals(profile)) return true;
        return false;
    }
}
