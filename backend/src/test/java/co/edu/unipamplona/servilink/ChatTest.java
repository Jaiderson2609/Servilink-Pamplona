package co.edu.unipamplona.servilink;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RF-033: conversación entre el hogar y el técnico asignado, mientras el servicio está en curso. */
class ChatTest extends PruebaApi {

    String hogar, tecnico;

    @BeforeEach
    void escenario() throws Exception {
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
    }

    String msg(String texto) { return "{\"texto\":\"" + texto + "\"}"; }

    @Test
    void antesDeAsignarNoHayChat() throws Exception {
        long sol = ((Number) campo(enviar(hogar, "/api/v1/solicitudes", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/mensajes", msg("Hola")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Chat cerrado"));
        pedir(hogar, "/api/v1/solicitudes/" + sol + "/mensajes").andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Chat no disponible"));
    }

    @Test
    void ambosEscribenYLeenEnOrdenYMarcaCuálesSonSuyos() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        String url = "/api/v1/solicitudes/" + sol + "/mensajes";
        enviar(hogar, url, msg("¿A qué hora llega?")).andExpect(status().isCreated()).andExpect(jsonPath("$.mio").value(true));
        enviar(tecnico, url, msg("En 20 minutos")).andExpect(status().isCreated());
        pedir(hogar, url).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].texto").value("¿A qué hora llega?"))
                .andExpect(jsonPath("$[0].mio").value(true)).andExpect(jsonPath("$[1].mio").value(false));
        pedir(tecnico, url).andExpect(jsonPath("$[0].mio").value(false)).andExpect(jsonPath("$[1].mio").value(true));
    }

    @Test
    void laConsultaIncrementalSoloTraeLoNuevo() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        String url = "/api/v1/solicitudes/" + sol + "/mensajes";
        long primero = ((Number) campo(enviar(hogar, url, msg("uno")), "$.id")).longValue();
        enviar(tecnico, url, msg("dos"));
        enviar(hogar, url, msg("tres"));
        pedir(tecnico, url + "?despues=" + primero).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].texto").value("dos"));
        pedir(tecnico, url + "?despues=9999").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void soloLasPartesParticipan() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        String url = "/api/v1/solicitudes/" + sol + "/mensajes";
        String intruso = cuenta("HOGAR", "intruso@correo.co");
        String otroTecnico = cuenta("TECNICO", "otro@correo.co");
        enviar(intruso, url, msg("hola")).andExpect(status().isForbidden());
        pedir(intruso, url).andExpect(status().isForbidden());
        enviar(otroTecnico, url, msg("hola")).andExpect(status().isForbidden());
        pedir(otroTecnico, url).andExpect(status().isForbidden());
        pedir(null, url).andExpect(status().isUnauthorized());
    }

    @Test
    void validaElTexto() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        String url = "/api/v1/solicitudes/" + sol + "/mensajes";
        enviar(hogar, url, msg("   ")).andExpect(status().isBadRequest());
        enviar(hogar, url, "{}").andExpect(status().isBadRequest());
        enviar(hogar, url, msg("a".repeat(501))).andExpect(status().isBadRequest());
        enviar(hogar, url, msg("a".repeat(500))).andExpect(status().isCreated());
    }

    @Test
    void alFinalizarSeConservaElHistorialPeroSeCierraLaEscritura() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        String url = "/api/v1/solicitudes/" + sol + "/mensajes";
        enviar(hogar, url, msg("Gracias por venir"));
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/llegada", null);
        enviar(tecnico, url, msg("Ya llegué")).andExpect(status().isCreated());   // E04 sigue abierto
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/trabajo-terminado", null);
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/confirmacion", null);
        enviar(hogar, url, msg("¿Todo bien?")).andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Chat cerrado"));
        pedir(hogar, url).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unaSolicitudCanceladaSinTecnicoNuncaTuvoChat() throws Exception {
        long sol = ((Number) campo(enviar(hogar, "/api/v1/solicitudes", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/cancelacion", null).andExpect(status().isOk());
        pedir(hogar, "/api/v1/solicitudes/" + sol + "/mensajes").andExpect(status().isConflict());
    }
}
