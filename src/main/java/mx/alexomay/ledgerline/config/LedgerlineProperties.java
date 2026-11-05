package mx.alexomay.ledgerline.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledgerline")
public record LedgerlineProperties(
        Jwt jwt, Limits limits, Spei spei, Kafka kafka, Demo demo, Clabe clabe, ZoneId timezone) {

    /** Firma de los tokens de acceso (HMAC-SHA256). */
    public record Jwt(String secret, Duration ttl) {}

    /** Montos en pesos. */
    public record Limits(BigDecimal perTransfer, BigDecimal daily) {}

    /** Secreto compartido con el banco para firmar las notificaciones SPEI. */
    public record Spei(String webhookSecret, Duration tolerance) {}

    public record Kafka(boolean enabled, String topic) {}

    public record Demo(boolean enabled, String adminPassword, BigDecimal maxDeposit) {}

    /** Código de banco y plaza con los que se generan las CLABE de los clientes. */
    public record Clabe(String bankCode, String branchCode) {}
}
