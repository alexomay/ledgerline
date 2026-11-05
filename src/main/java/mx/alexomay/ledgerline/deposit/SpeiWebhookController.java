package mx.alexomay.ledgerline.deposit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "SPEI")
class SpeiWebhookController {

    public record SpeiPayload(
            @NotBlank @Size(max = 30) String trackingKey,
            @NotBlank @Pattern(regexp = "\\d{18}") String clabe,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 120) String senderName,
            @NotBlank @Size(max = 60) String senderBank) {}

    private final SpeiWebhookHandler handler;

    SpeiWebhookController(SpeiWebhookHandler handler) {
        this.handler = handler;
    }

    /**
     * El cuerpo se recibe como texto crudo: la firma se calcula sobre los bytes
     * exactos que mandó el banco, antes de interpretar el JSON.
     */
    @PostMapping(value = "/api/webhooks/spei", consumes = "application/json")
    @Operation(summary = "Notificación de depósito SPEI entrante (firmada con HMAC)")
    ResponseEntity<SpeiWebhookHandler.Ack> receive(
            @RequestHeader(value = SpeiSignature.HEADER, required = false) String signature, @RequestBody String body) {
        return ResponseEntity.ok(handler.handle(body, signature));
    }
}
