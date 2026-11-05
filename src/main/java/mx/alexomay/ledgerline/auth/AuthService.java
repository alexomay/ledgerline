package mx.alexomay.ledgerline.auth;

import java.util.Locale;
import mx.alexomay.ledgerline.account.AccountService;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final LoginAttempts attempts;

    /**
     * Hash de una contraseña aleatoria. Si el correo no existe se compara contra
     * él, para que la respuesta tarde lo mismo y no revele qué correos están
     * registrados.
     */
    private final String dummyHash;

    AuthService(
            UserRepository users,
            AccountService accounts,
            PasswordEncoder passwordEncoder,
            TokenService tokens,
            LoginAttempts attempts) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.attempts = attempts;
        this.dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public User register(String fullName, String email, String password, User.Role role) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "Ya existe una cuenta con ese correo.");
        }
        User user = users.save(new User(normalized, passwordEncoder.encode(password), fullName.trim(), role));
        if (role == User.Role.USER) {
            accounts.openCustomerAccount(user.getId());
        }
        return user;
    }

    public TokenService.IssuedToken login(String email, String password) {
        if (attempts.isBlocked(email)) {
            throw new ApiException(
                    ErrorCode.TOO_MANY_ATTEMPTS, "Demasiados intentos fallidos. Espera unos minutos.");
        }
        User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        boolean ok = passwordEncoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok) {
            attempts.recordFailure(email);
            throw new ApiException(ErrorCode.BAD_CREDENTIALS, "Correo o contraseña incorrectos.");
        }
        attempts.reset(email);
        return tokens.issue(user);
    }

    public TokenService.IssuedToken issue(User user) {
        return tokens.issue(user);
    }
}
