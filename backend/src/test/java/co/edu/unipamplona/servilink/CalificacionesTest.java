package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RF-043: cada parte califica a la otra una vez, solo con el servicio finalizado (E05). */
class CalificacionesTest extends PruebaApi {

    String hogar, tecnico;

    @BeforeEach
    void escenario() throws Exception {
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
    }

    String cal(int estrellas, String comentario) {
        return "{\"estrellas\":" + estrellas + (comentario == null ? "" : ",\"comentario\":\"" + comentario + "\"") + "}";
    }

    @Test
    void noSeCalificaAntesDeFinalizar() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/calificacion", cal(5, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Servicio no finalizado"));
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/calificacion", cal(5, null)).andExpect(status().isConflict());
    }

    @Test
    void ambasPartesSeCalificanUnaVezCadaUna() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        finalizar(hogar, tecnico, sol);
        long hogarId = idDe(hogar), tecnicoId = idDe(tecnico);
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/calificacion", cal(5, "Excelente trabajo")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.destinatarioId").value(tecnicoId)).andExpect(jsonPath("$.estrellas").value(5));
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/calificacion", cal(4, null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.destinatarioId").value(hogarId));
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/calificacion", cal(1, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Calificacion duplicada"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM calificaciones", Integer.class)).isEqualTo(2);
    }

    @Test
    void soloLasPartesCalifican() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        finalizar(hogar, tecnico, sol);
        enviar(cuenta("HOGAR", "intruso@correo.co"), "/api/v1/solicitudes/" + sol + "/calificacion", cal(1, null)).andExpect(status().isForbidden());
        enviar(cuenta("TECNICO", "otro@correo.co"), "/api/v1/solicitudes/" + sol + "/calificacion", cal(1, null)).andExpect(status().isForbidden());
        enviar(hogar, "/api/v1/solicitudes/9999/calificacion", cal(3, null)).andExpect(status().isNotFound());
    }

    @Test
    void validaLasEstrellasYElComentario() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        finalizar(hogar, tecnico, sol);
        String url = "/api/v1/solicitudes/" + sol + "/calificacion";
        enviar(hogar, url, cal(0, null)).andExpect(status().isBadRequest());
        enviar(hogar, url, cal(6, null)).andExpect(status().isBadRequest());
        enviar(hogar, url, "{}").andExpect(status().isBadRequest());
        enviar(hogar, url, cal(5, "x".repeat(301))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM calificaciones", Integer.class)).isZero();
    }

    @Test
    void laReputacionPromediaYAparecaAlElegirTecnico() throws Exception {
        pedir(hogar, "/api/v1/tecnicos/" + idDe(tecnico) + "/reputacion").andExpect(status().isOk())
                .andExpect(jsonPath("$.calificaciones").value(0)).andExpect(jsonPath("$.promedio").doesNotExist());

        long s1 = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        finalizar(hogar, tecnico, s1);
        enviar(hogar, "/api/v1/solicitudes/" + s1 + "/calificacion", cal(5, null)).andExpect(status().isCreated());

        String hogar2 = cuenta("HOGAR", "h2@correo.co");
        long s2 = asignado(hogar2, tecnico, "Revisar la instalación de gas");
        finalizar(hogar2, tecnico, s2);
        enviar(hogar2, "/api/v1/solicitudes/" + s2 + "/calificacion", cal(4, null)).andExpect(status().isCreated());

        pedir(hogar, "/api/v1/tecnicos/" + idDe(tecnico) + "/reputacion").andExpect(jsonPath("$.promedio").value(4.5))
                .andExpect(jsonPath("$.calificaciones").value(2));

        // El hogar que elige ve el promedio de cada técnico que se postuló.
        String hogar3 = cuenta("HOGAR", "h3@correo.co");
        long s3 = ((Number) campo(enviar(hogar3, "/api/v1/solicitudes", solicitudJson("Cambiar la cerradura de la puerta")), "$.id")).longValue();
        enviar(tecnico, "/api/v1/solicitudes/" + s3 + "/postulaciones", "{\"precioCop\":20000}").andExpect(status().isCreated());
        pedir(hogar3, "/api/v1/solicitudes/" + s3 + "/postulaciones").andExpect(jsonPath("$[0].calificacionPromedio").value(4.5))
                .andExpect(jsonPath("$[0].calificaciones").value(2));
    }

    @Test
    void laReputacionSoloExisteParaTecnicos() throws Exception {
        pedir(hogar, "/api/v1/tecnicos/" + idDe(hogar) + "/reputacion").andExpect(status().isNotFound());
        pedir(hogar, "/api/v1/tecnicos/9999/reputacion").andExpect(status().isNotFound());
        pedir(null, "/api/v1/tecnicos/" + idDe(tecnico) + "/reputacion").andExpect(status().isUnauthorized());
    }

    @Test
    void laBaseGarantizaUnaCalificacionPorParteAunqueSeSaltenLaAplicacion() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        finalizar(hogar, tecnico, sol);
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/calificacion", cal(5, null)).andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DuplicateKeyException.class, () ->
                jdbc.update("INSERT INTO calificaciones (solicitud_id, autor_id, destinatario_id, estrellas, creada_en) "
                        + "VALUES (?, ?, ?, 3, now())", sol, idDe(hogar), idDe(tecnico)));
    }
}
