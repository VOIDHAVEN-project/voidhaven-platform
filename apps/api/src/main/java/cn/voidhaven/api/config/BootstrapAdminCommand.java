package cn.voidhaven.api.config;

import cn.voidhaven.api.domain.Account;
import cn.voidhaven.api.domain.Role;
import cn.voidhaven.api.persistence.AccountRepository;
import cn.voidhaven.api.persistence.RoleRepository;
import cn.voidhaven.api.service.ActivationService;
import cn.voidhaven.api.service.AuditService;
import cn.voidhaven.api.support.Ids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;

/**
 * Explicit, one-shot operator command for creating the first production administrator.
 * This component is never active in the normal production web process, so activation
 * credentials cannot leak into ordinary application or aggregated startup logs.
 */
@Component
@Profile("bootstrap-admin")
public class BootstrapAdminCommand implements ApplicationRunner {
    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final ActivationService activations;
    private final AuditService audit;
    private final Environment environment;
    private final TransactionTemplate transactions;

    @Value("${voidhaven.bootstrap-admin-login:}")
    private String adminLogin;

    @Value("${voidhaven.public-base-url:}")
    private String publicBaseUrl;

    public BootstrapAdminCommand(AccountRepository accounts, RoleRepository roles,
            ActivationService activations, AuditService audit, Environment environment,
            TransactionTemplate transactions) {
        this.accounts = accounts;
        this.roles = roles;
        this.activations = activations;
        this.audit = audit;
        this.environment = environment;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (Arrays.stream(environment.getActiveProfiles()).noneMatch("production"::equals)) {
            throw new IllegalStateException("bootstrap-admin must run together with the production profile");
        }
        if (adminLogin == null || adminLogin.isBlank()) {
            throw new IllegalStateException("VOIDHAVEN_BOOTSTRAP_ADMIN_LOGIN is required");
        }
        String token = transactions.execute(status -> {
            if (accounts.count() > 0) {
                throw new IllegalStateException("Bootstrap refused because the accounts table is not empty");
            }
            Role adminRole = roles.findByCode("ADMIN").orElseGet(() -> roles.save(new Role("ADMIN")));
            Role superAdminRole = roles.findByCode("SUPER_ADMIN").orElseGet(() -> roles.save(new Role("SUPER_ADMIN")));
            Account account = new Account(Ids.ulid(), adminLogin.trim(), "!ACTIVATION_REQUIRED!", "PENDING");
            account.addRole(adminRole);
            account.addRole(superAdminRole);
            accounts.saveAndFlush(account);
            String issued = activations.issue(account.getId());
            audit.record("SYSTEM", "SYSTEM", "PRODUCTION_BOOTSTRAP_ADMIN_CREATED", "ACCOUNT", account.getId(),
                    "{}", "{\"qualificationStatus\":\"PENDING\"}", null, null);
            return issued;
        });
        if (token == null) throw new IllegalStateException("Bootstrap transaction did not issue an activation token");

        String base = publicBaseUrl.replaceAll("/$", "");
        // Deliberately use the attached one-shot command output rather than SLF4J. Run this
        // only with an ephemeral `docker compose run --rm` process and never pipe the output
        // into application logging or evidence files.
        System.out.println("One-time activation URL (valid for 24 hours; do not record it):");
        System.out.println(base + "/auth/activate?token=" + token);
    }
}
