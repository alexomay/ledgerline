package mx.alexomay.ledgerline.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import mx.alexomay.ledgerline.common.Money;
import mx.alexomay.ledgerline.config.LedgerlineProperties;
import mx.alexomay.ledgerline.transfer.TransferLimits;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Cuentas")
@SecurityRequirement(name = "bearer")
class AccountController {

    record Limits(BigDecimal perTransfer, BigDecimal daily, BigDecimal usedToday, BigDecimal remainingToday) {}

    record AccountView(
            UUID id, String holder, String email, String clabe, String currency, BigDecimal balance, Limits limits) {}

    record MovementView(
            long id,
            Instant at,
            String kind,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String description,
            String counterparty,
            String concept,
            String reference) {
        static MovementView of(AccountService.Movement m) {
            return new MovementView(
                    m.id(),
                    m.at(),
                    m.kind(),
                    Money.toPesos(m.amount()),
                    Money.toPesos(m.balanceAfter()),
                    m.description(),
                    m.counterparty(),
                    m.concept(),
                    m.reference());
        }
    }

    record MovementPageView(List<MovementView> items, Long nextCursor) {}

    private final AccountService accounts;
    private final TransferLimits limits;
    private final LedgerlineProperties props;

    AccountController(AccountService accounts, TransferLimits limits, LedgerlineProperties props) {
        this.accounts = accounts;
        this.limits = limits;
        this.props = props;
    }

    @GetMapping("/me")
    @Operation(summary = "Saldo, CLABE y límites disponibles")
    AccountView me(@AuthenticationPrincipal Jwt jwt) {
        Account a = accounts.customerAccount(userId(jwt));
        TransferLimits.Usage u = limits.usage(a.getId());
        return new AccountView(
                a.getId(),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"),
                a.getClabe(),
                a.getCurrency(),
                Money.toPesos(a.getBalance()),
                new Limits(
                        Money.toPesos(u.perTransfer()),
                        Money.toPesos(u.daily()),
                        Money.toPesos(u.usedToday()),
                        Money.toPesos(u.remainingToday())));
    }

    @GetMapping("/me/movements")
    @Operation(summary = "Estado de cuenta, del más reciente al más antiguo (paginado por cursor)")
    MovementPageView movements(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "20") int limit) {
        Account a = accounts.customerAccount(userId(jwt));
        AccountService.MovementPage page = accounts.movements(a.getId(), before, Math.clamp(limit, 1, 100));
        return new MovementPageView(page.items().stream().map(MovementView::of).toList(), page.nextCursor());
    }

    @GetMapping(value = "/me/movements.csv", produces = "text/csv")
    @Operation(summary = "Estado de cuenta completo en CSV")
    void movementsCsv(@AuthenticationPrincipal Jwt jwt, HttpServletResponse response) throws IOException {
        Account a = accounts.customerAccount(userId(jwt));
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"estado-de-cuenta-" + a.getClabe() + ".csv\"");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(props.timezone());
        PrintWriter out = new PrintWriter(response.getOutputStream(), false, StandardCharsets.UTF_8);
        out.print('﻿'); // BOM: Excel abre el archivo con acentos correctos
        out.println("fecha,tipo,contraparte,concepto,monto,saldo,referencia");
        Long cursor = null;
        do {
            AccountService.MovementPage page = accounts.movements(a.getId(), cursor, 500);
            for (AccountService.Movement m : page.items()) {
                out.println(String.join(
                        ",",
                        fmt.format(m.at()),
                        m.kind(),
                        csv(m.counterparty()),
                        csv(m.concept()),
                        Money.toPesos(m.amount()).toPlainString(),
                        Money.toPesos(m.balanceAfter()).toPlainString(),
                        csv(m.reference())));
            }
            cursor = page.nextCursor();
        } while (cursor != null);
        out.flush();
    }

    @GetMapping("/lookup")
    @Operation(summary = "Confirma a quién pertenece una CLABE (nombre parcial)")
    AccountService.Holder lookup(@RequestParam String clabe) {
        return accounts.holder(clabe);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Escapa un campo CSV y neutraliza fórmulas (=, +, -, @) para evitar inyección en Excel. */
    static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
