package cn.voidhaven.api.support;

import cn.voidhaven.api.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

public final class LoginIds {
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._-]{4,100}$");

    private LoginIds() { }

    public static String normalize(String loginId) {
        String normalized = loginId == null ? "" : loginId.trim();
        if (!PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOGIN_ID_INVALID", "登录名需为 4 到 100 个字母、数字、点、下划线或连字符。");
        }
        return normalized;
    }
}
