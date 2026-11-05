package mx.alexomay.ledgerline.ledger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import mx.alexomay.ledgerline.common.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administración")
@SecurityRequirement(name = "bearer")
class AdminController {

    record Overview(
            long customers,
            BigDecimal customerFunds,
            BigDecimal speiClearing,
            long transfersLast24h,
            BigDecimal volumeLast24h,
            long outboxPending,
            long outboxFailing) {}

    private final ReconciliationService reconciliation;
    private final JdbcClient jdbc;

    AdminController(ReconciliationService reconciliation, JdbcClient jdbc) {
        this.reconciliation = reconciliation;
        this.jdbc = jdbc;
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "Verifica que el libro cuadre: saldos contra movimientos, asientos en cero y total en cero")
    ReconciliationService.Report reconcile() {
        return reconciliation.run();
    }

    @GetMapping("/overview")
    @Operation(summary = "Indicadores generales de la operación")
    @Transactional(readOnly = true)
    Overview overview() {
        return jdbc.sql("""
                        select
                          (select count(*) from accounts where kind = 'CUSTOMER'),
                          (select coalesce(sum(balance), 0) from accounts where kind = 'CUSTOMER'),
                          (select coalesce(sum(balance), 0) from accounts where code = 'SPEI_CLEARING'),
                          (select count(*) from transfers where created_at > now() - interval '24 hours'),
                          (select coalesce(sum(amount), 0) from transfers where created_at > now() - interval '24 hours'),
                          (select count(*) from outbox_events where published_at is null),
                          (select count(*) from outbox_events where published_at is null and attempts > 0)
                        """)
                .query((rs, i) -> new Overview(
                        rs.getLong(1),
                        Money.toPesos(rs.getLong(2)),
                        Money.toPesos(rs.getLong(3)),
                        rs.getLong(4),
                        Money.toPesos(rs.getLong(5)),
                        rs.getLong(6),
                        rs.getLong(7)))
                .single();
    }
}
