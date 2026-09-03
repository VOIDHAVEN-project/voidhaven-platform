package cn.voidhaven.api.config;

import cn.voidhaven.api.domain.*;
import cn.voidhaven.api.persistence.*;
import cn.voidhaven.api.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component @Profile("local")
public class LocalDataInitializer implements ApplicationRunner {
    private final RoleRepository roles; private final AccountRepository accounts; private final PartnerProfileRepository profiles; private final ContentItemRepository items; private final ContentRevisionRepository revisions; private final PasswordEncoder encoder; private final JdbcTemplate jdbc;
    @Value("${voidhaven.bootstrap-admin-login:}") String adminLogin;
    @Value("${voidhaven.bootstrap-admin-password:}") String adminPassword;
    @Value("${voidhaven.bootstrap-partner-login:}") String partnerLogin;
    @Value("${voidhaven.bootstrap-partner-password:}") String partnerPassword;
    @Value("${voidhaven.public-base-url:http://localhost:8080}") String publicBaseUrl;
    public LocalDataInitializer(RoleRepository roles, AccountRepository accounts, PartnerProfileRepository profiles, ContentItemRepository items, ContentRevisionRepository revisions, PasswordEncoder encoder, JdbcTemplate jdbc) { this.roles = roles; this.accounts = accounts; this.profiles = profiles; this.items = items; this.revisions = revisions; this.encoder = encoder; this.jdbc = jdbc; }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        Role partner = role("PARTNER"), admin = role("ADMIN"), superAdmin = role("SUPER_ADMIN");
        if (!adminLogin.isBlank() && !adminPassword.isBlank() && accounts.findByLoginId(adminLogin).isEmpty()) { Account account = new Account(Ids.ulid(), adminLogin, encoder.encode(adminPassword)); account.addRole(admin); account.addRole(superAdmin); accounts.save(account); }
        if (!partnerLogin.isBlank() && !partnerPassword.isBlank() && accounts.findByLoginId(partnerLogin).isEmpty()) { Account account = new Account(Ids.ulid(), partnerLogin, encoder.encode(partnerPassword)); account.addRole(partner); accounts.saveAndFlush(account); profiles.save(new PartnerProfile(Ids.ulid(), account.getId(), "本地合作方（合成）", 3)); }
        if (items.count() == 0) {
            publish(ContentKind.COMPETITION, "校内创新挑战赛（示例）", "用于本地验收的合成竞赛条目，展示官方来源和截止日期的呈现方式。", "2026-10-15", "本地合成来源说明");
            publish(ContentKind.RESOURCE, "从问题到原型：学习路径（示例）", "用于本地验收的合成学习资源，包含可追溯来源和中文导读。", null, "本地合成来源说明");
            publish(ContentKind.RECRUITMENT, "数据可视化小组招募（示例）", "用于本地验收的合成团队招募；站内仅提供受控后续入口，不记录组队过程。", "2026-09-30", "本地合成来源说明");
        }
    }
    private Role role(String code) { return roles.findByCode(code).orElseGet(() -> roles.save(new Role(code))); }
    private void publish(ContentKind kind, String title, String summary, String deadlineDate, String sourceName) {
        ContentItem item = new ContentItem(Ids.ulid(), kind, WorkflowStatus.PUBLISHED, Visibility.PUBLIC, RecordState.ACTIVE);
        item.setModuleKey(kind == ContentKind.COMPETITION ? "competition" : kind == ContentKind.RECRUITMENT ? "recruitment" : "resource");
        item = items.saveAndFlush(item);
        String details = deadlineDate == null ? "{}" : "{\"deadlineDate\":\"" + deadlineDate + "\"}";
        ContentRevision revision = new ContentRevision(Ids.ulid(), item, 1, title, summary, "{\"version\":1,\"blocks\":[{\"type\":\"paragraph\",\"text\":\"这是仅用于本地环境的合成内容。请在生产前由审核人员替换为经核验的实际来源。\"}]}", "zh-CN", null, publicBaseUrl.replaceAll("/$", "") + "/local-fixtures/source", sourceName, "本地合成数据，仅用于验收", "VOIDHAVEN 本地合成内容", "NOT_ALLOWED", "NOT_ALLOWED", details, null);
        revision = revisions.saveAndFlush(revision);
        writeTypedDetails(kind, revision.getId(), deadlineDate);
        item.setCurrentRevision(revision); item.setPublishedRevision(revision); item.publish(); items.saveAndFlush(item);
    }
    private void writeTypedDetails(ContentKind kind, String revisionId, String deadlineDate) {
        if (kind == ContentKind.COMPETITION) jdbc.update("insert into competition_revision_details (revision_id, deadline_date, official_rules_url) values (?, ?, ?)", revisionId, deadlineDate, publicBaseUrl.replaceAll("/$", "") + "/local-fixtures/source");
        else if (kind == ContentKind.RESOURCE) jdbc.update("insert into resource_revision_details (revision_id, resource_type) values (?, ?)", revisionId, "GUIDE");
        else jdbc.update("insert into recruitment_revision_details (revision_id, deadline_date) values (?, ?)", revisionId, deadlineDate);
    }
}
