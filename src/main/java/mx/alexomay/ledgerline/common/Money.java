package mx.alexomay.ledgerline.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversión entre pesos y centavos. Dentro del sistema todo se maneja en
 * centavos ({@code long}) para no acumular errores de punto flotante; los pesos
 * con dos decimales solo existen en la frontera de la API.
 */
public final class Money {

    private Money() {}

    public static long toCents(BigDecimal pesos) {
        if (pesos.stripTrailingZeros().scale() > 2) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "El monto admite máximo dos decimales.");
        }
        return pesos.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    public static BigDecimal toPesos(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    public static String format(long cents) {
        return "$" + String.format("%,.2f", toPesos(cents));
    }
}
