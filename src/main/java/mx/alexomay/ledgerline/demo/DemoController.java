package mx.alexomay.ledgerline.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mx.alexomay.ledgerline.account.AccountService;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.ErrorCode;
import mx.alexomay.ledgerline.common.Money;
import mx.alexomay.ledgerline.config.LedgerlineProperties;
import mx.alexomay.ledgerline.deposit.SpeiSignature;
import mx.alexomay.ledgerline.deposit.SpeiWebhookHandler;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Simulador de banco para la demo: arma una notificación SPEI, la firma con el
 * secreto compartido y la entrega al mismo endpoint que usaría un banco real.
 */
@RestController
@RequestMapping("/api/demo")
@Tag(name = "Demo")
@SecurityRequirement(name = "bearer")
class DemoController {

    record DepositRequest(
            @NotNull @DecimalMin(value = "1.00", message = "El depósito mínimo es $1.00.")
                    @Digits(integer = 10, fraction = 2) BigDecimal amount) {}

    record DemoAccount(String label, String email, String password) {}

    record DemoInfo(boolean enabled, BigDecimal maxDeposit, List<DemoAccount> accounts) {}

    private static final String[] BANKS = {"BBVA MÉXICO", "BANORTE", "SANTANDER", "HSBC", "BANAMEX"};
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LedgerlineProperties props;
    private final AccountService accounts;
    private final SpeiSignature signatures;
    private final JsonMapper json;
    private final SpeiWebhookHandler bank;

    DemoController(
            LedgerlineProperties props,
            AccountService accounts,
            SpeiSignature signatures,
            JsonMapper json,
            SpeiWebhookHandler bank) {
        this.props = props;
        this.accounts = accounts;
        this.signatures = signatures;
        this.json = json;
        this.bank = bank;
    }

    @GetMapping
    @Operation(summary = "Indica si está activo el modo demo y con qué cuentas se puede entrar")
    DemoInfo info() {
        if (!props.demo().enabled()) {
            return new DemoInfo(false, null, List.of());
        }
        List<DemoAccount> demoAccounts = new ArrayList<>(List.of(
                new DemoAccount("Ana García", "ana@demo.mx", DemoSeeder.DEMO_PASSWORD),
                new DemoAccount("Luis Hernández", "luis@demo.mx", DemoSeeder.DEMO_PASSWORD)));
        String admin = props.demo().adminPassword();
        if (admin != null && !admin.isBlank()) {
            demoAccounts.add(new DemoAccount("Administración", "admin@ledgerline.dev", admin));
        }
        return new DemoInfo(true, props.demo().maxDeposit(), demoAccounts);
    }

    @PostMapping("/deposits")
    @Operation(summary = "Simula que alguien te deposita por SPEI (solo en modo demo)")
    SpeiWebhookHandler.Ack deposit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DepositRequest req) {
        if (!props.demo().enabled()) {
            throw new ApiException(ErrorCode.DEMO_DISABLED, "El simulador solo está disponible en modo demo.");
        }
        if (req.amount().compareTo(props.demo().maxDeposit()) > 0) {
            throw new ApiException(
                    ErrorCode.LIMIT_EXCEEDED,
                    "En la demo el depósito máximo es " + Money.format(Money.toCents(props.demo().maxDeposit())) + ".");
        }
        String clabe = accounts.customerAccount(UUID.fromString(jwt.getSubject())).getClabe();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trackingKey", trackingKey());
        payload.put("clabe", clabe);
        payload.put("amount", req.amount());
        payload.put("senderName", "Depósito de prueba");
        payload.put("senderBank", BANKS[RANDOM.nextInt(BANKS.length)]);
        String body = json.writeValueAsString(payload);

        // Mismo camino que una notificación real: firma, verificación, validación y abono.
        return bank.handle(body, signatures.sign(body));
    }

    /** Clave de rastreo con el formato habitual: prefijo, fecha y consecutivo. */
    private static String trackingKey() {
        return "LLDEMO" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%010d", RANDOM.nextLong(10_000_000_000L));
    }
}
