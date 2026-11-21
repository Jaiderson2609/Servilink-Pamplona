package co.edu.unipamplona.servilink;

import static co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas.T0;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaIntegracion;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Un técnico verificado abre una solicitud abierta antes de postularse (es la pantalla donde cotiza); nadie más ve lo ajeno. */
class DetalleAbiertaTest extends PruebaIntegracion {

    long hogar, tecnico;

    @BeforeEach
    void escenario() {
        hogar = usuario("HOGAR", "Hogar Uno", "3110000001");
        tecnico = tecnico("Tecnico Demo", 50_000);
    }


    @Test
    void elTecnicoVerificadoVeLaSolicitudAbiertaSinPostularseYSinContacto() throws Exception {
        long e01 = solicitud(hogar, "E01", T0.plus(Duration.ofMinutes(10)));
        mvc.perform(get("/api/v1/service-requests/" + e01).header("Authorization", "Bearer " + token("TECNICO", tecnico)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("E01")).andExpect(jsonPath("$.contacto").doesNotExist());
        long e02 = solicitud(usuario("HOGAR", "Hogar Dos", "3110000002"), "E02", T0.plus(Duration.ofMinutes(5)));
        mvc.perform(get("/api/v1/service-requests/" + e02).header("Authorization", "Bearer " + token("TECNICO", tecnico)))
                .andExpect(status().isOk());
    }

    @Test
    void elTecnicoSinVerificarNoLaVe() throws Exception {
        long sin = usuario("TECNICO", "Tecnico Sin Verificar", "3000000001");
        long sol = solicitud(hogar, "E01", T0.plus(Duration.ofMinutes(10)));
        mvc.perform(get("/api/v1/service-requests/" + sol).header("Authorization", "Bearer " + token("TECNICO", sin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaSolicitudYaAsignadaOCerradaNoSeAbreAlResto() throws Exception {
        for (String estado : new String[] {"E03", "E04", "E05", "E06", "E07"}) {
            jdbc.update("TRUNCATE postulaciones, solicitudes RESTART IDENTITY CASCADE");
            long sol = solicitud(hogar, estado, T0.plus(Duration.ofMinutes(10)));
            mvc.perform(get("/api/v1/service-requests/" + sol).header("Authorization", "Bearer " + token("TECNICO", tecnico)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void otroHogarNoVeLaSolicitudAjena() throws Exception {
        long sol = solicitud(hogar, "E01", T0.plus(Duration.ofMinutes(10)));
        long otro = usuario("HOGAR", "Hogar Ajeno", "3110000009");
        mvc.perform(get("/api/v1/service-requests/" + sol).header("Authorization", "Bearer " + token("HOGAR", otro)))
                .andExpect(status().isForbidden());
    }
}
