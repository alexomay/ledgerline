package mx.alexomay.ledgerline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class LedgerlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerlineApplication.class, args);
    }
}
