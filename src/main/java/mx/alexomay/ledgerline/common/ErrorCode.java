package mx.alexomay.ledgerline.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION(HttpStatus.BAD_REQUEST, "Datos inválidos"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Monto inválido"),
    INVALID_CLABE(HttpStatus.BAD_REQUEST, "CLABE inválida"),
    MISSING_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Falta la llave de idempotencia"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas"),
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "Firma inválida"),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Sin permiso"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Cuenta no encontrada"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "Correo ya registrado"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "Llave de idempotencia reutilizada"),
    SAME_ACCOUNT(HttpStatus.UNPROCESSABLE_CONTENT, "Misma cuenta"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "Saldo insuficiente"),
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "Límite excedido"),
    DEMO_DISABLED(HttpStatus.NOT_FOUND, "No disponible");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
