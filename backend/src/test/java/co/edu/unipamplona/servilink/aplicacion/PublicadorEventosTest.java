package co.edu.unipamplona.servilink.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.edu.unipamplona.servilink.aplicacion.PublicadorEventos.SolicitudCambio;
import co.edu.unipamplona.servilink.dominio.Solicitud;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mientras el hogar no elige, el técnico postulado debe enterarse en tiempo real de la negociación. */
class PublicadorEventosTest {

    final List<Object> publicados = new ArrayList<>();
    final PublicadorEventos eventos = new PublicadorEventos(publicados::add);

    Solicitud solicitud() {
        Instant t = Instant.parse("2026-11-20T12:00:00Z");
        return new Solicitud(1L, 2, "Cambiar un tomacorriente", 7.37, -72.65, t.plusSeconds(600), t);
    }

    @Test
    void sinTecnicoAsignadoAvisaAlPostulado() {
        eventos.solicitud(solicitud(), 55L);
        assertEquals(55L, ((SolicitudCambio) publicados.get(0)).tecnicoId());
    }

    @Test
    void sinPostuladoNiAsignadoElTecnicoQuedaNulo() {
        eventos.solicitud(solicitud());
        assertNull(((SolicitudCambio) publicados.get(0)).tecnicoId());
    }
}
