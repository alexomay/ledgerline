package mx.alexomay.ledgerline.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
class AuthController {

    record RegisterRequest(
            @NotBlank(message = "Escribe tu nombre.") @Size(max = 120) String fullName,
            @NotBlank(message = "Escribe tu correo.") @Email(message = "Correo no válido.") @Size(max = 254)
                    String email,
            @NotBlank @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres.")
                    String password) {}

    record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {
        static TokenResponse of(TokenService.IssuedToken t) {
            return new TokenResponse(t.token(), "Bearer", t.expiresAt());
        }
    }

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un usuario con su cuenta y CLABE, y devuelve un token")
    TokenResponse register(@Valid @RequestBody RegisterRequest req) {
        User user = auth.register(req.fullName(), req.email(), req.password(), User.Role.USER);
        return TokenResponse.of(auth.issue(user));
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve un token de acceso (JWT)")
    TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return TokenResponse.of(auth.login(req.email(), req.password()));
    }
}
