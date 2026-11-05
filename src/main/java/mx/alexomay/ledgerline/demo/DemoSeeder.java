package mx.alexomay.ledgerline.demo;

import java.util.List;
import mx.alexomay.ledgerline.account.AccountService;
import mx.alexomay.ledgerline.auth.AuthService;
import mx.alexomay.ledgerline.auth.User;
import mx.alexomay.ledgerline.auth.UserRepository;
import mx.alexomay.ledgerline.config.LedgerlineProperties;
import mx.alexomay.ledgerline.deposit.DepositService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * En modo demo crea dos clientes con saldo inicial y, si se definió
 * LEDGERLINE_ADMIN_PASSWORD, un administrador. Solo corre si aún no existen.
 */
@Component
@ConditionalOnProperty(name = "ledgerline.demo.enabled", havingValue = "true")
class DemoSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "demo-ledgerline";

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private record DemoUser(String name, String email, long initialCents) {}

    private static final List<DemoUser> USERS = List.of(
            new DemoUser("Ana García López", "ana@demo.mx", 2_500_000),
            new DemoUser("Luis Hernández Ruiz", "luis@demo.mx", 1_200_000));

    private final UserRepository users;
    private final AuthService auth;
    private final AccountService accounts;
    private final DepositService deposits;
    private final LedgerlineProperties props;

    DemoSeeder(
            UserRepository users,
            AuthService auth,
            AccountService accounts,
            DepositService deposits,
            LedgerlineProperties props) {
        this.users = users;
        this.auth = auth;
        this.accounts = accounts;
        this.deposits = deposits;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (DemoUser u : USERS) {
            if (users.existsByEmailIgnoreCase(u.email())) {
                continue;
            }
            User user = auth.register(u.name(), u.email(), DEMO_PASSWORD, User.Role.USER);
            String clabe = accounts.customerAccount(user.getId()).getClabe();
            deposits.receive(new DepositService.SpeiNotice(
                    "LLSEED" + clabe.substring(6), clabe, u.initialCents(), "Nómina Empresa Demo SA de CV", "BBVA MÉXICO"));
            log.info("Usuario demo creado: {} ({})", u.email(), clabe);
        }

        String adminPassword = props.demo().adminPassword();
        if (adminPassword != null && !adminPassword.isBlank() && !users.existsByEmailIgnoreCase("admin@ledgerline.dev")) {
            auth.register("Administración", "admin@ledgerline.dev", adminPassword, User.Role.ADMIN);
            log.info("Administrador demo creado: admin@ledgerline.dev");
        }
    }
}
