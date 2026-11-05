package mx.alexomay.ledgerline.deposit;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.ErrorCode;
import mx.alexomay.ledgerline.config.LedgerlineProperties;
import org.springframework.stereotype.Component;

/**
 * Firma de las notificaciones del banco: {@code t=<epoch>,v1=<hex>}, donde v1
 * es HMAC-SHA256 de {@code "<epoch>.<cuerpo>"} con el secreto compartido.
 *
 * <p>Incluir la hora en lo firmado impide reenviar una notificación vieja
 * capturada (ataque de repetición): se rechaza si tiene más de 5 minutos.
 */
@Component
public class SpeiSignature {

    public static final String HEADER = "X-Spei-Signature";

    private final byte[] secret;
    private final Duration tolerance;
    private final Clock clock;

    SpeiSignature(LedgerlineProperties props, Clock clock) {
        this.secret = props.spei().webhookSecret().getBytes(StandardCharsets.UTF_8);
        this.tolerance = props.spei().tolerance();
        this.clock = clock;
    }

    public String sign(String body) {
        long t = clock.instant().getEpochSecond();
        return "t=" + t + ",v1=" + hmac(t + "." + body);
    }

    public void verify(String body, String header) {
        if (header == null) {
            throw invalid();
        }
        Long t = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            switch (kv[0]) {
                case "t" -> t = parseLong(kv[1]);
                case "v1" -> v1 = kv[1];
                default -> {}
            }
        }
        if (t == null || v1 == null) {
            throw invalid();
        }
        Duration age = Duration.between(Instant.ofEpochSecond(t), clock.instant()).abs();
        if (age.compareTo(tolerance) > 0) {
            throw new ApiException(ErrorCode.INVALID_SIGNATURE, "La notificación expiró.");
        }
        byte[] expected = hmac(t + "." + body).getBytes(StandardCharsets.US_ASCII);
        // Comparación en tiempo constante: no revela cuántos caracteres coinciden.
        if (!MessageDigest.isEqual(expected, v1.getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_SIGNATURE, "Firma de la notificación inválida.");
    }
}
