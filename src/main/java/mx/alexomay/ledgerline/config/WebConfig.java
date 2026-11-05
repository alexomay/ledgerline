package mx.alexomay.ledgerline.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Ledgerline API",
                version = "1.0",
                description = "Billetera digital con libro contable de doble partida, transferencias idempotentes "
                        + "y depósitos SPEI firmados."))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class WebConfig implements WebMvcConfigurer {

    /** Rutas de la aplicación Angular: el navegador las resuelve, el servidor entrega index.html. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String route : new String[] {"/entrar", "/registro", "/enviar", "/movimientos", "/admin"}) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
