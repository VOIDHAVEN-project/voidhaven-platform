package cn.voidhaven.api.config;

import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.service.*;
import cn.voidhaven.api.support.Ids;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Arrays;

@Component
@Profile("recover-super-admin")
public class RecoverSuperAdminCommand implements ApplicationRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final SecurityRevocationService revocations;
    private final AuditService audit;
    private final Environment environment;
    private final TransactionTemplate transactions;
    public RecoverSuperAdminCommand(AccountRepository accounts, PasswordEncoder encoder, SecurityRevocationService revocations,
                                    AuditService audit, Environment environment, TransactionTemplate transactions) {
        this.accounts = accounts; this.encoder = encoder; this.revocations = revocations;
        this.audit = audit; this.environment = environment; this.transactions = transactions;
    }
    public void run(ApplicationArguments args) {
        if (!"none".equals(environment.getProperty("spring.main.web-application-type"))
                || !BootstrapAdminCommand.isApprovedEnvironment(environment.getActiveProfiles()))
            throw new IllegalStateException("Recovery requires non-Web mode and an approved environment");
        if (!args.getOptionNames().equals(java.util.Set.of("login", "operator", "reason")))
            throw new IllegalStateException("Only --login, --operator and --reason are accepted; passwords must never be arguments");
        String login = required(args, "login"), operator = required(args, "operator"), reason = required(args, "reason");
        var console = System.console();
        if (console == null) throw new IllegalStateException("An attached interactive console is required");
        char[] password = console.readPassword("New password: ");
        char[] confirmation = console.readPassword("Confirm password: ");
        try {
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation) || password.length < 12 || password.length > 128)
                throw new IllegalStateException("Password confirmation or length is invalid");
            recover(login, new String(password), operator, reason);
            console.printf("Recovery completed. All previous sessions and credentials are invalid. Review account and authorization history.\n");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
    void recover(String login, String password, String operator, String reason) {
        transactions.executeWithoutResult(status -> {
            var account = accounts.findByLoginId(login).flatMap(value -> accounts.findByIdForUpdate(value.getId()))
                    .filter(value -> value.hasRole("SUPER_ADMIN") && (value.isActive() || value.isPending()))
                    .orElseThrow(() -> new IllegalStateException("Only an existing active or pending SUPER_ADMIN may be recovered"));
            if (account.isPending()) account.activateWithPassword(encoder.encode(password));
            else account.changePassword(encoder.encode(password));
            revocations.revoke(account);
            audit.record("SYSTEM", "SYSTEM", "SUPER_ADMIN_RECOVERED", "ACCOUNT", account.getId(), "{}",
                    "{\"sessionsRevoked\":true}", "operator=" + operator + "; " + reason, Ids.ulid());
        });
    }
    private String required(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.get(0).isBlank() || values.get(0).length() > ("reason".equals(name) ? 800 : 100))
            throw new IllegalStateException("A single nonempty --" + name + " is required");
        return values.get(0).trim();
    }
}
