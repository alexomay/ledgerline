package mx.alexomay.ledgerline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import mx.alexomay.ledgerline.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class TransferApiTest extends IntegrationTest {

    @Test
    void transferMovesMoneyAndShowsUpInBothStatements() throws Exception {
        Session ana = register("Ana Prueba");
        Session luis = register("Luis Prueba");
        deposit(ana, "1000.00");

        transfer(ana, UUID.randomUUID().toString(), luis.clabe(), "250.75", "Renta de junio")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.amount").value(250.75));

        assertThat(balance(ana)).isEqualByComparingTo("749.25");
        assertThat(balance(luis)).isEqualByComparingTo("250.75");

        JsonNode anaMovements = read(mvc.perform(
                get("/api/accounts/me/movements").header("Authorization", "Bearer " + ana.token())));
        assertThat(anaMovements.get("items")).hasSize(2);
        JsonNode last = anaMovements.get("items").get(0);
        assertThat(last.get("amount").decimalValue()).isEqualByComparingTo("-250.75");
        assertThat(last.get("balanceAfter").decimalValue()).isEqualByComparingTo("749.25");
        assertThat(last.get("counterparty").asString()).isEqualTo("Luis Prueba");
        assertThat(last.get("concept").asString()).isEqualTo("Renta de junio");

        JsonNode luisLast = read(mvc.perform(
                        get("/api/accounts/me/movements").header("Authorization", "Bearer " + luis.token())))
                .get("items")
                .get(0);
        assertThat(luisLast.get("amount").decimalValue()).isEqualByComparingTo("250.75");
        assertThat(luisLast.get("counterparty").asString()).isEqualTo("Ana Prueba");
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsSameTransferWithoutChargingTwice() throws Exception {
        Session ana = register("Ana Reintento");
        Session luis = register("Luis Reintento");
        deposit(ana, "500.00");
        String key = UUID.randomUUID().toString();

        String firstId = read(transfer(ana, key, luis.clabe(), "100.00", "Comida").andExpect(status().isCreated()))
                .get("id")
                .asString();
        String secondId = read(transfer(ana, key, luis.clabe(), "100.00", "Comida")
                        .andExpect(status().isOk())
                        .andExpect(header().string("Idempotent-Replayed", "true")))
                .get("id")
                .asString();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(balance(ana)).isEqualByComparingTo("400.00");
        assertThat(balance(luis)).isEqualByComparingTo("100.00");
    }

    @Test
    void reusingKeyForDifferentOperationIsRejected() throws Exception {
        Session ana = register("Ana Llave");
        Session luis = register("Luis Llave");
        deposit(ana, "500.00");
        String key = UUID.randomUUID().toString();

        transfer(ana, key, luis.clabe(), "100.00", "Comida").andExpect(status().isCreated());
        transfer(ana, key, luis.clabe(), "300.00", "Comida")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(balance(ana)).isEqualByComparingTo("400.00");
    }

    @Test
    void failedTransferDoesNotBurnTheKey() throws Exception {
        Session ana = register("Ana Sin Saldo");
        Session luis = register("Luis Sin Saldo");
        String key = UUID.randomUUID().toString();

        transfer(ana, key, luis.clabe(), "50.00", "Préstamo")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        deposit(ana, "60.00");
        transfer(ana, key, luis.clabe(), "50.00", "Préstamo").andExpect(status().isCreated());
        assertThat(balance(ana)).isEqualByComparingTo("10.00");
    }

    @Test
    void enforcesPerTransferAndDailyLimits() throws Exception {
        Session ana = register("Ana Limites");
        Session luis = register("Luis Limites");
        for (int i = 0; i < 6; i++) {
            deposit(ana, "20000.00");
        }

        transfer(ana, UUID.randomUUID().toString(), luis.clabe(), "50000.01", "Uno")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));

        transfer(ana, UUID.randomUUID().toString(), luis.clabe(), "50000.00", "Uno").andExpect(status().isCreated());
        transfer(ana, UUID.randomUUID().toString(), luis.clabe(), "50000.00", "Dos").andExpect(status().isCreated());
        transfer(ana, UUID.randomUUID().toString(), luis.clabe(), "0.01", "Tres")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("límite diario")));

        mvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + ana.token()))
                .andExpect(jsonPath("$.limits.remainingToday").value(0));
    }

    @Test
    void validatesInput() throws Exception {
        Session ana = register("Ana Validacion");
        deposit(ana, "100.00");

        transfer(ana, UUID.randomUUID().toString(), "032180000118359718", "10.00", "Mal dígito")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CLABE"));
        transfer(ana, UUID.randomUUID().toString(), "032180000118359719", "10.00", "No existe")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        transfer(ana, UUID.randomUUID().toString(), ana.clabe(), "10.00", "A mí mismo")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));
        transfer(ana, UUID.randomUUID().toString(), ana.clabe(), "10.001", "Fracción")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());

        mvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + ana.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("toClabe", ana.clabe(), "amount", 1, "concept", "Sin llave"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    void lookupShowsMaskedHolderName() throws Exception {
        Session ana = register("Ana Consulta");
        Session luis = register("Luis Alberto Consulta");
        mvc.perform(get("/api/accounts/lookup")
                        .param("clabe", luis.clabe())
                        .header("Authorization", "Bearer " + ana.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedName").value("Luis A*** C***"));
    }

    @Test
    void csvExportEscapesFormulas() throws Exception {
        Session ana = register("Ana Csv");
        Session luis = register("Luis Csv");
        deposit(ana, "100.00");
        transfer(ana, UUID.randomUUID().toString(), luis.clabe(), "1.00", "=HYPERLINK(\"http://x\")")
                .andExpect(status().isCreated());

        String csv = mvc.perform(get("/api/accounts/me/movements.csv").header("Authorization", "Bearer " + ana.token()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("fecha,tipo,contraparte").contains("\"'=HYPERLINK(\"\"http://x\"\")\"");
    }

    @Test
    void securityRules() throws Exception {
        mvc.perform(get("/api/accounts/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts/me").header("Authorization", "Bearer no-es-un-token"))
                .andExpect(status().isUnauthorized());

        Session ana = register("Ana Seguridad");
        mvc.perform(get("/api/admin/reconciliation").header("Authorization", "Bearer " + ana.token()))
                .andExpect(status().isForbidden());

        String admin = login("admin@ledgerline.dev", "admin-de-pruebas");
        mvc.perform(get("/api/admin/reconciliation").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true));
    }

    @Test
    void locksLoginAfterRepeatedFailures() throws Exception {
        Session ana = register("Ana Bloqueo");
        String email = jdbc.sql("select email from users where id = ?").param(ana.userId()).query(String.class).single();
        String bad = json.writeValueAsString(Map.of("email", email, "password", "incorrecta"));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isUnauthorized());
        }
        String good = json.writeValueAsString(Map.of("email", email, "password", "contrasena-segura"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(good))
                .andExpect(status().isTooManyRequests());
    }
}
