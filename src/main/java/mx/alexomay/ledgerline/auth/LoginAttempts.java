package mx.alexomay.ledgerline.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Bloqueo temporal tras varios intentos fallidos de inicio de sesión con el
 * mismo correo, para frenar ataques de fuerza bruta. Vive en memoria: con
 * varias instancias habría que moverlo a Redis.
 */
@Component
class LoginAttempts {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Failures(int count, Instant firstAt) {}

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginAttempts(Clock clock) {
        this.clock = clock;
    }

    boolean isBlocked(String email) {
        Failures f = failures.get(key(email));
        if (f == null) {
            return false;
        }
        if (f.firstAt().plus(WINDOW).isBefore(clock.instant())) {
            failures.remove(key(email), f);
            return false;
        }
        return f.count() >= MAX_FAILURES;
    }

    void recordFailure(String email) {
        Instant now = clock.instant();
        failures.merge(key(email), new Failures(1, now), (old, n) -> old.firstAt().plus(WINDOW).isBefore(now)
                ? n
                : new Failures(old.count() + 1, old.firstAt()));
    }

    void reset(String email) {
        failures.remove(key(email));
    }

    private static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
