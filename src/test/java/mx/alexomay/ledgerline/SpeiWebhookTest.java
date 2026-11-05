package mx.alexomay.ledgerline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mx.alexomay.ledgerline.deposit.SpeiSignature;
import mx.alexomay.ledgerline.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SpeiWebhookTest extends IntegrationTest {

    @Autowired
    SpeiSignature signature;

    private String notice(String trackingKey, String clabe, String amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trackingKey", trackingKey);
        body.put("clabe", clabe);
        body.put("amount", new java.math.BigDecimal(amount));
        body.put("senderName", "Empresa Pagadora SA");
        body.put("senderBank", "BANORTE");
        return json.writeValueAsString(body);
    }

    private ResultActions send(String body, String signatureHeader) throws Exception {
        var request = post("/api/webhooks/spei").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signatureHeader != null) {
            request.header(SpeiSignature.HEADER, signatureHeader);
        }
        return mvc.perform(request);
    }

    @Test
    void creditsSignedDepositOnlyOnceEvenIfBankResends() throws Exception {
        Session ana = register("Ana Spei");
        String trackingKey = "BNTE" + UUID.randomUUID().toString().substring(0, 20);
        String body = notice(trackingKey, ana.clabe(), "1234.56");

        send(body, signature.sign(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("credited"));
        send(body, signature.sign(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));

        assertThat(balance(ana)).isEqualByComparingTo("1234.56");
    }

    @Test
    void rejectsUnsignedOrForgedNotifications() throws Exception {
        Session ana = register("Ana Falsa");
        String body = notice("FAKE" + UUID.randomUUID().toString().substring(0, 20), ana.clabe(), "99999.00");

        send(body, null).andExpect(status().isUnauthorized());
        send(body, "t=" + (System.currentTimeMillis() / 1000) + ",v1=" + "0".repeat(64))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        // Firma válida de otro cuerpo: el atacante cambió el monto.
        String original = notice("ORIG" + UUID.randomUUID().toString().substring(0, 20), ana.clabe(), "1.00");
        send(body, signature.sign(original)).andExpect(status().isUnauthorized());

        assertThat(balance(ana)).isEqualByComparingTo("0");
    }

    @Test
    void rejectsDepositToUnknownClabe() throws Exception {
        String body = notice("NOPE" + UUID.randomUUID().toString().substring(0, 20), "032180000118359719", "10.00");
        send(body, signature.sign(body)).andExpect(status().isNotFound());
    }
}
