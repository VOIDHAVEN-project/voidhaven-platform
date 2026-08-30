package cn.voidhaven.api.support;
import java.security.SecureRandom;
public final class Ids {
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private Ids() { }
    public static String ulid() { StringBuilder id = new StringBuilder(26); for (int i = 0; i < 26; i++) id.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]); return id.toString(); }
}
