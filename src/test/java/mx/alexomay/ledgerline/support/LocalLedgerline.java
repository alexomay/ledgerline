package mx.alexomay.ledgerline.support;

import java.nio.file.Path;
import javax.sql.DataSource;
import mx.alexomay.ledgerline.LedgerlineApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Arranca la aplicación en local sin Docker, con PostgreSQL embebido y datos
 * persistentes en ~/.ledgerline/pg, en modo demo.
 *
 * <pre>./mvnw spring-boot:test-run</pre>
 */
public class LocalLedgerline {

    public static void main(String[] args) {
        System.setProperty("ledgerline.demo.enabled", System.getProperty("ledgerline.demo.enabled", "true"));
        System.setProperty(
                "ledgerline.demo.admin-password",
                System.getProperty("ledgerline.demo.admin-password", "admin-ledgerline"));
        SpringApplication.from(LedgerlineApplication::main).with(LocalDatabase.class).run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalDatabase {

        @Bean
        DataSource dataSource() {
            Path dir = Path.of(System.getProperty("user.home"), ".ledgerline", "pg");
            return EmbeddedDatabase.dataSource(EmbeddedDatabase.start(dir), 10);
        }
    }
}
