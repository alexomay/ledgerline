package mx.alexomay.ledgerline.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "ledgerline.demo.enabled=true",
            "ledgerline.demo.admin-password=admin-de-pruebas",
            // El relay se ejecuta a mano en las pruebas que lo necesitan.
            "ledgerline.outbox.interval=3600000"
        })
@Import(EmbeddedDatabase.class)
public abstract class IntegrationTest {

    public record Session(String token, String clabe, UUID userId) {}

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected JdbcClient jdbc;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    protected Session register(String name) throws Exception {
        String email = name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@pruebas.mx";
        String body = json.writeValueAsString(Map.of("fullName", name, "email", email, "password", "contrasena-segura"));
        String token = read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated()))
                .get("accessToken")
                .asString();
        JsonNode me = read(mvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()));
        UUID userId = jdbc.sql("select id from users where email = ?").param(email).query(UUID.class).single();
        return new Session(token, me.get("clabe").asString(), userId);
    }

    protected String login(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        return read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isOk()))
                .get("accessToken")
                .asString();
    }

    protected void deposit(Session s, String amount) throws Exception {
        mvc.perform(post("/api/demo/deposits")
                        .header("Authorization", "Bearer " + s.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("amount", new BigDecimal(amount)))))
                .andExpect(status().isOk());
    }

    protected ResultActions transfer(Session from, String key, String toClabe, String amount, String concept)
            throws Exception {
        return mvc.perform(post("/api/transfers")
                .header("Authorization", "Bearer " + from.token())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        Map.of("toClabe", toClabe, "amount", new BigDecimal(amount), "concept", concept))));
    }

    protected BigDecimal balance(Session s) throws Exception {
        return read(mvc.perform(get("/api/accounts/me").header("Authorization", "Bearer " + s.token()))
                        .andExpect(status().isOk()))
                .get("balance")
                .decimalValue();
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
