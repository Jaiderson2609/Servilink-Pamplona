package co.edu.unipamplona.servilink;

import static co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import co.edu.unipamplona.servilink.apoyo.PruebaIntegracion;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Bandeja de salida: el aviso nunca se pierde ni se duplica aunque Firebase falle. */
class NotificacionTest extends PruebaIntegracion {

    @Autowired NotificacionService servicio;
    long hogar, tecnico, sol, pos;

    @BeforeEach
    void escenario() {
        hogar = usuario("HOGAR", "Hogar Uno", "3110000001");
        tecnico = tecnico("Tecnico Demo", 50_000);
        sol = solicitud(hogar, "E02", T0.plus(Duration.ofMinutes(5)));
        pos = postulacion(sol, tecnico, 20_000);
    }

    void asignar() throws Exception {
        mvc.perform(patch("/api/v1/applications/" + pos + "/select").header("Authorization", "Bearer " + token("HOGAR", hogar))
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
    }

    String estado() { return jdbc.queryForObject("SELECT estado FROM notificaciones_pendientes", String.class); }
    int intentos() { return jdbc.queryForObject("SELECT intentos FROM notificaciones_pendientes", Integer.class); }

    @Test
    void conFirebaseDisponibleElAvisoQuedaEnviada() throws Exception {
        asignar();
        assertThat(estado()).isEqualTo("ENVIADA");
        assertThat(intentos()).isEqualTo(1);
    }

    @Test
    void siFirebaseFallaLaAsignacionSigueYElAvisoQuedaPendienteHastaReintentar() throws Exception {
        notificador.caido = true;
        asignar(); // responde 200: el fallo del aviso no revierte la asignación
        assertThat(estado()).isEqualTo("PENDIENTE");
        assertThat(intentos()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM solicitudes WHERE id = ?", String.class, sol)).isEqualTo("E03");

        notificador.caido = false;
        assertThat(servicio.reintentarPendientes()).isEqualTo(1);
        assertThat(estado()).isEqualTo("ENVIADA");
        assertThat(intentos()).isEqualTo(2);
        assertThat(notificador.enviados).hasSize(1);
    }

    @Test
    void reintentarSinPendientesNoHaceNada() {
        assertThat(servicio.reintentarPendientes()).isZero();
    }

    @Test
    void unAvisoYaEnviadoNoSeVuelveAEnviar() throws Exception {
        asignar();
        servicio.reintentarPendientes();
        servicio.despachar(jdbc.queryForObject("SELECT id FROM notificaciones_pendientes", Long.class));
        assertThat(notificador.intentos).hasSize(1);
        assertThat(intentos()).isEqualTo(1);
    }

    @Test
    void elReintentoHttpDeLaAsignacionNoDuplicaElAviso() throws Exception {
        asignar();
        asignar();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_pendientes", Integer.class)).isEqualTo(1);
        assertThat(notificador.enviados).hasSize(1);
    }
}
