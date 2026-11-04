package co.edu.unipamplona.servilink.apoyo;

import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ConfiguracionPruebas {
    public static final Instant T0 = Instant.parse("2026-11-04T12:00:00Z");

    @Bean @Primary
    RelojMutable relojDePruebas() { return new RelojMutable(T0); }

    @Bean @Primary
    NotificadorFalso notificadorDePruebas() { return new NotificadorFalso(); }
}
