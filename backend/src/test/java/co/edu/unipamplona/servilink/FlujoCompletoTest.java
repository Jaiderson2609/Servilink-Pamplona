package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** El recorrido completo de un servicio, solo por la API: de la creación a la comisión cobrada. */
class FlujoCompletoTest extends PruebaApi {

    @Test
    void unServicioVaDeE01AE05YCobraLaComisionUnaSolaVez() throws Exception {
        String hogar = cuenta("HOGAR", "hogar@correo.co");
        String tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);

        var creada = enviar(hogar, "/api/v1/solicitudes", solicitudJson("Cambiar un tomacorriente roto")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("E01")).andExpect(header().exists("Location"));
        long sol = ((Number) campo(creada, "$.id")).longValue();

        pedir(tecnico, "/api/v1/solicitudes/abiertas").andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(sol));

        var post = enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000,\"mensaje\":\"Llego en 20 min\"}")
                .andExpect(status().isCreated());
        long pos = ((Number) campo(post, "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT estado FROM solicitudes WHERE id = ?", String.class, sol)).isEqualTo("E02");

        pedir(hogar, "/api/v1/solicitudes/" + sol + "/postulaciones").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].precioCop").value(20000)).andExpect(jsonPath("$[0].mensaje").value("Llego en 20 min"));

        // Antes de asignar, el técnico no ve el contacto del hogar.
        pedir(tecnico, "/api/v1/solicitudes/" + sol).andExpect(status().isOk()).andExpect(jsonPath("$.contacto").doesNotExist());

        enviar(hogar, "/api/v1/solicitudes/" + sol + "/asignacion", "{\"postulacionId\":" + pos + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.comisionRetenidaCop").value(2000));
        pedir(tecnico, "/api/v1/billetera").andExpect(jsonPath("$.saldoDisponibleCop").value(48000))
                .andExpect(jsonPath("$.saldoRetenidoCop").value(2000));
        pedir(tecnico, "/api/v1/solicitudes/" + sol).andExpect(jsonPath("$.contacto.nombre").exists());

        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/llegada", null).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("E04"));
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/confirmacion", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Trabajo no terminado"));
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/trabajo-terminado", null).andExpect(status().isOk());
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/confirmacion", null).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("E05"));
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/confirmacion", null).andExpect(status().isOk()); // idempotente

        pedir(tecnico, "/api/v1/billetera").andExpect(jsonPath("$.saldoDisponibleCop").value(48000))
                .andExpect(jsonPath("$.saldoRetenidoCop").value(0));
        assertThat(jdbc.queryForList("SELECT tipo FROM libro_contable WHERE solicitud_id = ? ORDER BY id", String.class, sol))
                .containsExactly("RETENCION", "COMISION");
        assertThat(jdbc.queryForObject("SELECT sum(valor) FROM libro_contable WHERE tipo = 'COMISION'", Long.class)).isEqualTo(2000L);
        assertThat(notificador.enviados).hasSize(4); // asignado, llegó, terminó, confirmó

        // Terminado el servicio, el hogar y el técnico pueden empezar otro.
        enviar(hogar, "/api/v1/solicitudes", solicitudJson("Revisar la instalación de la cocina")).andExpect(status().isCreated());
    }

    @Test
    void cancelarUnServicioAsignadoLiberaLaComision() throws Exception {
        String hogar = cuenta("HOGAR", "hogar@correo.co");
        String tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
        long sol = ((Number) campo(enviar(hogar, "/api/v1/solicitudes", solicitudJson("Destapar el lavamanos")), "$.id")).longValue();
        long pos = ((Number) campo(enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":30000}"), "$.id")).longValue();
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/asignacion", "{\"postulacionId\":" + pos + "}").andExpect(status().isOk());

        enviar(hogar, "/api/v1/solicitudes/" + sol + "/cancelacion", "{}").andExpect(status().isBadRequest()); // exige motivo
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/cancelacion", "{\"motivo\":\"Ya lo resolví yo mismo\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("E07"));

        pedir(tecnico, "/api/v1/billetera").andExpect(jsonPath("$.saldoDisponibleCop").value(50000))
                .andExpect(jsonPath("$.saldoRetenidoCop").value(0));
        assertThat(jdbc.queryForList("SELECT tipo FROM libro_contable WHERE solicitud_id = ? ORDER BY id", String.class, sol))
                .containsExactly("RETENCION", "LIBERACION");
        assertThat(jdbc.queryForObject("SELECT cancelada_por FROM solicitudes WHERE id = ?", String.class, sol)).isEqualTo("HOGAR");
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/cancelacion", "{\"motivo\":\"Otra vez, por si acaso\"}").andExpect(status().isConflict());
    }

    @Test
    void elTecnicoTambienPuedeCancelarConMotivoYElHogarQuedaLibre() throws Exception {
        String hogar = cuenta("HOGAR", "hogar@correo.co");
        String tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
        long sol = ((Number) campo(enviar(hogar, "/api/v1/solicitudes", solicitudJson("Cambiar la cerradura de la puerta")), "$.id")).longValue();
        long pos = ((Number) campo(enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":30000}"), "$.id")).longValue();
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/asignacion", "{\"postulacionId\":" + pos + "}").andExpect(status().isOk());
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/cancelacion", "{\"motivo\":\"Se me dañó el carro\"}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT cancelada_por FROM solicitudes WHERE id = ?", String.class, sol)).isEqualTo("TECNICO");
        enviar(hogar, "/api/v1/solicitudes", solicitudJson("Cambiar la cerradura de la puerta")).andExpect(status().isCreated());
    }
}
