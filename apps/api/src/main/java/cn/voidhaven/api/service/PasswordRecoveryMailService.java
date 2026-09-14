package cn.voidhaven.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PasswordRecoveryMailService {
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from;

    public PasswordRecoveryMailService(JavaMailSender sender,
            @Value("${voidhaven.password-self-service-enabled:false}") boolean enabled,
            @Value("${voidhaven.password-recovery-mail-from:}") String from) {
        this.sender = sender;
        this.enabled = enabled;
        this.from = from == null ? "" : from.trim();
    }

    public boolean isOperational() { return enabled && !from.isBlank(); }

    public void sendVerificationCode(String email, String token, Instant expiresAt) {
        send(email, "VOIDHAVEN 恢复邮箱验证码",
                "你正在为 VOIDHAVEN 账号绑定恢复邮箱。\n\n验证码：\n" + token
                        + "\n\n该验证码将在 " + expiresAt + " 前有效，只能使用一次。请在当前登录的工作台中粘贴；不要转发。\n");
    }

    public void sendPasswordResetToken(String email, String token, Instant expiresAt) {
        send(email, "VOIDHAVEN 密码重置凭据",
                "有人为你的 VOIDHAVEN 账号申请了密码重置。\n\n一次性重置凭据：\n" + token
                        + "\n\n该凭据将在 " + expiresAt + " 前有效，只能使用一次。请打开 VOIDHAVEN 的密码重置页面手动粘贴；不要转发。"
                        + "\n\n如果不是你发起的，请忽略本邮件，原密码不会改变。\n");
    }

    private void send(String email, String subject, String body) {
        if (!isOperational()) throw new IllegalStateException("Password recovery mail is not configured");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
