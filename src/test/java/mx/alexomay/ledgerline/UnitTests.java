package mx.alexomay.ledgerline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import mx.alexomay.ledgerline.account.Clabe;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.Money;
import mx.alexomay.ledgerline.config.LedgerlineProperties;
import mx.alexomay.ledgerline.deposit.SpeiSignature;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UnitTests {

    @Nested
    class ClabeTests {

        @Test
        void validatesKnownClabe() {
            // CLABE de ejemplo publicada en la documentación de Banxico.
            assertThat(Clabe.isValid("032180000118359719")).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"032180000118359718", "03218000011835971", "0321800001183597190", "03218000011835971a", ""})
        void rejectsWrongCheckDigitOrFormat(String clabe) {
            assertThat(Clabe.isValid(clabe)).isFalse();
        }

        @Test
        void generatedClabesAreValidAndKeepBankAndBranch() {
            for (int i = 0; i < 1_000; i++) {
                String clabe = Clabe.generate("999", "180");
                assertThat(clabe).startsWith("999180").hasSize(18);
                assertThat(Clabe.isValid(clabe)).isTrue();
            }
        }
    }

    @Nested
    class MoneyTests {

        @Test
        void convertsBetweenPesosAndCents() {
            assertThat(Money.toCents(new BigDecimal("150.5"))).isEqualTo(15_050);
            assertThat(Money.toCents(new BigDecimal("0.01"))).isEqualTo(1);
            assertThat(Money.toCents(new BigDecimal("100.000"))).isEqualTo(10_000);
            assertThat(Money.toPesos(15_050)).isEqualByComparingTo("150.50");
        }

        @Test
        void rejectsFractionsOfCents() {
            assertThatThrownBy(() -> Money.toCents(new BigDecimal("10.005"))).isInstanceOf(ApiException.class);
        }

        @Test
        void neverUsesFloatingPoint() {
            // 0.1 + 0.2 en double da 0.30000000000000004; en centavos es exacto.
            assertThat(Money.toCents(new BigDecimal("0.1")) + Money.toCents(new BigDecimal("0.2")))
                    .isEqualTo(Money.toCents(new BigDecimal("0.3")));
        }
    }

    @Nested
    class SpeiSignatureTests {

        private final Instant now = Instant.parse("2026-11-05T18:00:00Z");

        private SpeiSignature at(Instant instant) {
            LedgerlineProperties props = new LedgerlineProperties(
                    null,
                    null,
                    new LedgerlineProperties.Spei("secreto-del-banco", Duration.ofMinutes(5)),
                    null,
                    null,
                    null,
                    ZoneId.of("America/Mexico_City"));
            return newSignature(props, Clock.fixed(instant, ZoneOffset.UTC));
        }

        @Test
        void acceptsItsOwnSignature() {
            String body = "{\"amount\":100}";
            at(now).verify(body, at(now).sign(body));
        }

        @Test
        void rejectsTamperedBody() {
            String header = at(now).sign("{\"amount\":100}");
            assertThatThrownBy(() -> at(now).verify("{\"amount\":100000}", header))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void rejectsReplayedOldNotification() {
            String body = "{\"amount\":100}";
            String header = at(now).sign(body);
            assertThatThrownBy(() -> at(now.plusSeconds(301)).verify(body, header))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("expiró");
        }

        @Test
        void rejectsMissingOrMalformedHeader() {
            assertThatThrownBy(() -> at(now).verify("{}", null)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> at(now).verify("{}", "v1=abc")).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> at(now).verify("{}", "t=x,v1=abc")).isInstanceOf(ApiException.class);
        }
    }

    /** El constructor es de paquete para que solo Spring lo cree; aquí se usa por reflexión. */
    static SpeiSignature newSignature(LedgerlineProperties props, Clock clock) {
        try {
            var ctor = SpeiSignature.class.getDeclaredConstructor(LedgerlineProperties.class, Clock.class);
            ctor.setAccessible(true);
            return ctor.newInstance(props, clock);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
