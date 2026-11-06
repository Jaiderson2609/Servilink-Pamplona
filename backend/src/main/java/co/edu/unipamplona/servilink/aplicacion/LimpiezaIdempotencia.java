package co.edu.unipamplona.servilink.aplicacion;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Borra las claves de idempotencia con más de 24 horas (DDS 8.2). */
@Component
public class LimpiezaIdempotencia {

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public LimpiezaIdempotencia(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${servilink.limpieza-idempotencia-ms:3600000}", initialDelay = 60_000)
    public int limpiar() {
        return jdbc.update("DELETE FROM claves_idempotencia WHERE creada_en < ?",
                Timestamp.from(reloj.instant().minus(Duration.ofHours(24))));
    }
}
