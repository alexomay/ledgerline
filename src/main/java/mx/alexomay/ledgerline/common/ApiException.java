package mx.alexomay.ledgerline.common;

/** Error de negocio que la API traduce a un Problem Details (RFC 9457). */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
