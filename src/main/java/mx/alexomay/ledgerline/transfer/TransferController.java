package mx.alexomay.ledgerline.transfer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.ErrorCode;
import mx.alexomay.ledgerline.common.Money;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transferencias")
@SecurityRequirement(name = "bearer")
class TransferController {

    record TransferRequest(
            @NotBlank(message = "Escribe la CLABE de destino.") @Pattern(regexp = "\\d{18}", message = "La CLABE tiene 18 dígitos.")
                    String toClabe,
            @NotNull(message = "Escribe el monto.") @DecimalMin(value = "0.01", message = "El monto mínimo es $0.01.")
                    @Digits(integer = 10, fraction = 2, message = "Máximo dos decimales.")
                    BigDecimal amount,
            @NotBlank(message = "Escribe un concepto.") @Size(max = 140, message = "Máximo 140 caracteres.")
                    String concept) {}

    record TransferResponse(
            UUID id, String toClabe, BigDecimal amount, String concept, Instant createdAt, boolean replayed) {}

    private final TransferService service;

    TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Envía dinero a otra cuenta por CLABE",
            description = "Repetir la petición con el mismo Idempotency-Key devuelve la misma transferencia "
                    + "(200 y Idempotent-Replayed: true) en lugar de crear otra.")
    ResponseEntity<TransferResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Valor único por operación, por ejemplo un UUID") @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody TransferRequest req) {
        String key = idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 80) {
            throw new ApiException(ErrorCode.MISSING_IDEMPOTENCY_KEY, "Idempotency-Key debe tener entre 1 y 80 caracteres.");
        }
        TransferService.Result result = service.transfer(new TransferService.Command(
                UUID.fromString(jwt.getSubject()), key, req.toClabe(), Money.toCents(req.amount()), req.concept().trim()));
        Transfer t = result.transfer();
        TransferResponse body = new TransferResponse(
                t.getId(), req.toClabe(), Money.toPesos(t.getAmount()), t.getConcept(), t.getCreatedAt(), result.replayed());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(body);
    }
}
