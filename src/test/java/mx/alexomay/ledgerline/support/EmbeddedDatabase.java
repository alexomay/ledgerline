package mx.alexomay.ledgerline.support;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * PostgreSQL real (no H2) embebido en el proceso de pruebas: los bloqueos, los
 * triggers y las restricciones se comportan igual que en producción, y no hace
 * falta Docker para correr las pruebas.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedDatabase {

    private static EmbeddedPostgres postgres;

    static synchronized EmbeddedPostgres start(Path dataDirectory) {
        if (postgres == null) {
            try {
                EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
                // Mensajes del servidor en inglés: evita problemas de codificación con el idioma de Windows.
                builder.setServerConfig("lc_messages", "C");
                if (dataDirectory != null) {
                    builder.setDataDirectory(dataDirectory).setCleanDataDirectory(false).setPort(54329);
                }
                postgres = builder.start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return postgres;
    }

    static DataSource dataSource(EmbeddedPostgres pg, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(pg.getJdbcUrl("postgres", "postgres"));
        config.setMaximumPoolSize(poolSize);
        return new HikariDataSource(config);
    }

    @Bean
    DataSource dataSource() {
        return dataSource(start(null), 24);
    }
}
