package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas;
import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import co.edu.unipamplona.servilink.aplicacion.SolicitudService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Reglas de negocio y bordes de las ventanas (RN-01, RN-02, RN-06, RN-07, RN-08, RF-026, RF-039). */
class ReglasFlujoTest extends PruebaApi {

    @Autowired SolicitudService servicio;
    String hogar, tecnico;

    @BeforeEach
    void escenario() throws Exception {
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
    }

    long crear(String token, String descripcion) throws Exception {
        return ((Number) campo(enviar(token, "/api/v1/solicitudes", solicitudJson(descripcion)), "$.id")).longValue();
    }

    @Test
    void unHogarSoloPuedeTenerUnaSolicitudActiva() throws Exception {
        crear(hogar, "Cambiar un tomacorriente roto");
        enviar(hogar, "/api/v1/solicitudes", solicitudJson("Otra solicitud al mismo tiempo")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Solicitud activa"));
    }

    @Test
    void validaLaDescripcionElOficioYLaUbicacion() throws Exception {
        enviar(hogar, "/api/v1/solicitudes", solicitudJson("corta")).andExpect(status().isBadRequest());
        enviar(hogar, "/api/v1/solicitudes", "{\"oficioId\":99,\"descripcion\":\"Descripción suficiente\",\"ubicacion\":{\"lat\":7.3,\"lng\":-72.6}}")
                .andExpect(status().isBadRequest());
        enviar(hogar, "/api/v1/solicitudes", "{\"oficioId\":2,\"descripcion\":\"Descripción suficiente\",\"ubicacion\":{\"lat\":95,\"lng\":-72.6}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void laPrimeraPostulacionAbreLaVentanaDeEleccionDeCincoMinutos() throws Exception {
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        reloj.fijar(ConfiguracionPruebas.T0.plus(Duration.ofMinutes(3)));
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000}").andExpect(status().isCreated());
        var expira = jdbc.queryForObject("SELECT expira_en FROM solicitudes WHERE id = ?", java.sql.Timestamp.class, sol).toInstant();
        assertThat(expira).isEqualTo(ConfiguracionPruebas.T0.plus(Duration.ofMinutes(8)));
    }

    @Test
    void noSePuedePostularEnElInstanteExactoDeExpirarLaVentana() throws Exception {
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        reloj.fijar(ConfiguracionPruebas.T0.plus(Duration.ofMinutes(10)));
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Ventana expirada"));
        reloj.fijar(ConfiguracionPruebas.T0.plus(Duration.ofMinutes(10)).minusSeconds(1));
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000}").andExpect(status().isCreated());
    }

    @Test
    void cadaTecnicoSePostulaUnaSolaVez() throws Exception {
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000}").andExpect(status().isCreated());
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":15000}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Postulacion duplicada"));
    }

    @Test
    void elSaldoMinimoParaPostularseEsDeCuarentaMil() throws Exception {
        String pobre = cuenta("TECNICO", "pobre@correo.co");
        recargar(pobre, 39_000);
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        enviar(pobre, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Saldo insuficiente"));
        recargar(pobre, 1_000);
        enviar(pobre, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000}").andExpect(status().isCreated());
    }

    @Test
    void elPrecioDebeSerRazonable() throws Exception {
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":999}").andExpect(status().isBadRequest());
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{}").andExpect(status().isBadRequest());
    }

    @Test
    void noSePostulaAUnaSolicitudYaAsignadaNiInexistente() throws Exception {
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        long pos = ((Number) campo(enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000}"), "$.id")).longValue();
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/asignacion", "{\"postulacionId\":" + pos + "}").andExpect(status().isOk());
        String otro = cuenta("TECNICO", "otro@correo.co");
        recargar(otro, 50_000);
        enviar(otro, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":18000}").andExpect(status().isConflict());
        enviar(otro, "/api/v1/solicitudes/9999/postulaciones", "{\"precioCop\":18000}").andExpect(status().isNotFound());
    }

    @Test
    void unTecnicoConServicioActivoNoPuedePostularseAOtro() throws Exception {
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        long pos = ((Number) campo(enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000}"), "$.id")).longValue();
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/asignacion", "{\"postulacionId\":" + pos + "}").andExpect(status().isOk());
        String hogar2 = cuenta("HOGAR", "hogar2@correo.co");
        long sol2 = crear(hogar2, "Revisar la instalación de gas");
        enviar(tecnico, "/api/v1/solicitudes/" + sol2 + "/postulaciones", "{\"precioCop\":25000}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Tecnico ocupado"));
    }

    @Test
    void cancelarEnE01Ye02EsLibreYNoCobraNada() throws Exception {
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/cancelacion", null).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("E07"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM libro_contable WHERE tipo <> 'RECARGA'", Integer.class)).isZero();
        long otra = crear(hogar, "Una solicitud nueva, ahora sí");
        enviar(tecnico, "/api/v1/solicitudes/" + otra + "/postulaciones", "{\"precioCop\":20000}").andExpect(status().isCreated());
        enviar(hogar, "/api/v1/solicitudes/" + otra + "/cancelacion", "{\"motivo\":\"\"}").andExpect(status().isOk());
    }

    @Test
    void soloElDuenoCancelaYSoloElTecnicoAsignadoAvanza() throws Exception {
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        String intruso = cuenta("HOGAR", "intruso@correo.co");
        enviar(intruso, "/api/v1/solicitudes/" + sol + "/cancelacion", null).andExpect(status().isForbidden());
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/cancelacion", null).andExpect(status().isForbidden());
        long pos = ((Number) campo(enviar(tecnico, "/api/v1/solicitudes/" + sol + "/postulaciones", "{\"precioCop\":20000}"), "$.id")).longValue();
        enviar(hogar, "/api/v1/solicitudes/" + sol + "/asignacion", "{\"postulacionId\":" + pos + "}").andExpect(status().isOk());
        String otro = cuenta("TECNICO", "otro@correo.co");
        enviar(otro, "/api/v1/solicitudes/" + sol + "/llegada", null).andExpect(status().isForbidden());
        enviar(tecnico, "/api/v1/solicitudes/" + sol + "/trabajo-terminado", null).andExpect(status().isConflict()); // aún en E03
        enviar(intruso, "/api/v1/solicitudes/" + sol + "/confirmacion", null).andExpect(status().isForbidden());
        pedir(intruso, "/api/v1/solicitudes/" + sol).andExpect(status().isForbidden());
    }

    @Test
    void elBarridoExpiraLasSolicitudesVencidasYLiberaAlHogar() throws Exception {
        long sol = crear(hogar, "Cambiar un tomacorriente roto");
        reloj.fijar(ConfiguracionPruebas.T0.plus(Duration.ofMinutes(11)));
        assertThat(servicio.expirarVencidas()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM solicitudes WHERE id = ?", String.class, sol)).isEqualTo("E06");
        pedir(tecnico, "/api/v1/solicitudes/abiertas").andExpect(jsonPath("$").isEmpty());
        crear(hogar, "Una solicitud nueva después de expirar"); // RN-13: la vencida no se borra, se crea otra
        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitudes", Integer.class)).isEqualTo(2);
    }

    @Test
    void unaSolicitudVencidaNoBloqueaAlHogarAunqueNoHayaCorridoElBarrido() throws Exception {
        crear(hogar, "Cambiar un tomacorriente roto");
        reloj.fijar(ConfiguracionPruebas.T0.plus(Duration.ofMinutes(30)));
        enviar(hogar, "/api/v1/solicitudes", solicitudJson("Otra solicitud mucho después")).andExpect(status().isUnauthorized()); // el token de 15 min venció
        hogar = campo(enviar(null, "/api/v1/auth/login", "{\"correo\":\"hogar@correo.co\",\"clave\":\"clave-segura-1\"}"), "$.accessToken");
        enviar(hogar, "/api/v1/solicitudes", solicitudJson("Otra solicitud mucho después")).andExpect(status().isCreated());
    }

    @Test
    void lasSolicitudesDeCadaUsuarioSeListanAparte() throws Exception {
        crear(hogar, "Cambiar un tomacorriente roto");
        pedir(hogar, "/api/v1/solicitudes/mias").andExpect(jsonPath("$.length()").value(1));
        pedir(tecnico, "/api/v1/solicitudes/mias").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elCatalogoDeOficiosTieneLasCincoCategoriasIniciales() throws Exception {
        pedir(hogar, "/api/v1/oficios").andExpect(jsonPath("$.length()").value(5)).andExpect(jsonPath("$[0].nombre").value("Plomería"));
    }
}
