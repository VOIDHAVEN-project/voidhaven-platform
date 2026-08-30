package cn.voidhaven.api.config;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component @Profile("local")
public class LocalDataInitializer implements ApplicationRunner {
    private final RoleRepository roles; private final AccountRepository accounts; private final ContentItemRepository items; private final ContentRevisionRepository revisions; private final PasswordEncoder encoder;
    @Value("${voidhaven.bootstrap-admin-login:}") String adminLogin;
    @Value("${voidhaven.bootstrap-admin-password:}") String adminPassword;
    public LocalDataInitializer(RoleRepository roles, AccountRepository accounts, ContentItemRepository items, ContentRevisionRepository revisions, PasswordEncoder encoder) { this.roles = roles; this.accounts = accounts; this.items = items; this.revisions = revisions; this.encoder = encoder; }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        Role partner = role("PARTNER"), admin = role("ADMIN"), superAdmin = role("SUPER_ADMIN");
        if (!adminLogin.isBlank() && !adminPassword.isBlank() && accounts.findByLoginId(adminLogin).isEmpty()) { Account account = new Account(Ids.ulid(), adminLogin, encoder.encode(adminPassword)); account.addRole(admin); account.addRole(superAdmin); accounts.save(account); }
        if (items.count() == 0) {
            publish(ContentKind.COMPETITION, "校内创新挑战赛（示例）", "用于本地验收的合成竞赛条目，展示官方来源和截止日期的呈现方式。", "2026-10-15", "竞赛官方示例页");
            publish(ContentKind.RESOURCE, "从问题到原型：学习路径（示例）", "用于本地验收的合成学习资源，包含可追溯来源和中文导读。", null, "资源官方示例页");
            publish(ContentKind.RECRUITMENT, "数据可视化小组招募（示例）", "用于本地验收的合成团队招募；站内仅提供受控后续入口，不记录组队过程。", "2026-09-30", "招募官方示例页");
        }
    }
    private Role role(String code) { return roles.findByCode(code).orElseGet(() -> roles.save(new Role(code))); }
    private void publish(ContentKind kind, String title, String summary, String deadlineDate, String sourceName) {
        ContentItem item = new ContentItem(Ids.ulid(), kind, WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE); items.save(item);
        String details = deadlineDate == null ? "{}" : "{\"deadlineDate\":\"" + deadlineDate + "\"}";
        ContentRevision revision = new ContentRevision(Ids.ulid(), item, 1, title, summary, "{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"这是仅用于本地环境的合成内容。请在生产前由审核人员替换为经核验的实际来源。\"}]}", "zh-CN", "https://example.invalid/voidhaven-local-fixture", sourceName, "本地合成数据，仅用于验收", details, null);
        revisions.save(revision); item.setCurrentRevision(revision); item.setPublishedRevision(revision); items.save(item);
    }
}
