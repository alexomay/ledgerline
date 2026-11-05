package mx.alexomay.ledgerline.deposit;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.ErrorCode;
import mx.alexomay.ledgerline.common.Money;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Verifica la firma, valida y abona una notificación SPEI. */
@Component
public class SpeiWebhookHandler {

    public record Ack(UUID depositId, String status) {}

    private final SpeiSignature signatures;
    private final DepositService deposits;
    private final JsonMapper json;
    private final Validator validator;

    SpeiWebhookHandler(SpeiSignature signatures, DepositService deposits, JsonMapper json, Validator validator) {
        this.signatures = signatures;
        this.deposits = deposits;
        this.json = json;
        this.validator = validator;
    }

    public Ack handle(String body, String signature) {
        signatures.verify(body, signature);
        SpeiWebhookController.SpeiPayload p;
        try {
            p = json.readValue(body, SpeiWebhookController.SpeiPayload.class);
        } catch (JacksonException e) {
            throw new ApiException(ErrorCode.VALIDATION, "Notificación con formato inválido.");
        }
        Set<ConstraintViolation<SpeiWebhookController.SpeiPayload>> violations = validator.validate(p);
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION, "Notificación incompleta o inválida.");
        }
        DepositService.Outcome outcome = deposits.receive(new DepositService.SpeiNotice(
                p.trackingKey(), p.clabe(), Money.toCents(p.amount()), p.senderName(), p.senderBank()));
        return new Ack(outcome.depositId(), outcome.duplicate() ? "duplicate" : "credited");
    }
}
