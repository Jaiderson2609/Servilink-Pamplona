package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas;
import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Parámetros editables del administrador (ERS 4): valores por defecto, validación, auditoría y efecto real. */
class ParametrosTest extends PruebaApi {

    String admin, hogar, tecnico;

    @BeforeEach
    void escenario() throws Exception {
        admin = cuentaAdmin("admin@correo.co");
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
    }

    ResultActions poner(String token, String clave, String valor) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put("/api/v1/admin/parameters/" + clave)
                .header("Authorization", "Bearer " + token).contentType("application/json").content("{\"valor\":" + valor + "}"));
    }

    long solicitud() throws Exception {
        return ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
    }

    @Test
    void listaLosSeisParametrosConSusValoresPorDefecto() throws Exception {
        pedir(admin, "/api/v1/admin/parameters").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.clave=='APPLY_WINDOW_S')].valor").value(600))
                .andExpect(jsonPath("$[?(@.clave=='SELECT_WINDOW_S')].valor").value(300))
                .andExpect(jsonPath("$[?(@.clave=='MAX_TOTAL_S')].valor").value(900))
                .andExpect(jsonPath("$[?(@.clave=='COMMISSION_PCT')].valor").value(10.0))
                .andExpect(jsonPath("$[?(@.clave=='MIN_BALANCE')].valor").value(40000))
                .andExpect(jsonPath("$[?(@.clave=='SEARCH_RADIUS_M')].valor").value(5000));
    }

    @Test
    void cambiarUnParametroLoAuditaYSoloLoHaceElAdmin() throws Exception {
        poner(admin, "MIN_BALANCE", "30000").andExpect(status().isOk()).andExpect(jsonPath("$.valor").value(30000))
                .andExpect(jsonPath("$.defecto").value(40000));
        poner(admin, "MIN_BALANCE", "35000").andExpect(status().isOk()); // segundo cambio actualiza la misma fila
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parametros", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detalle FROM auditoria_admin WHERE accion = 'CAMBIAR_PARAMETRO' ORDER BY id DESC LIMIT 1", String.class))
                .isEqualTo("MIN_BALANCE: 30000 → 35000");
        poner(tecnico, "MIN_BALANCE", "1").andExpect(status().isForbidden());
        pedir(hogar, "/api/v1/admin/parameters").andExpect(status().isForbidden());
    }

    @Test
    void validaClaveRangoYEnteros() throws Exception {
        poner(admin, "NO_EXISTE", "5").andExpect(status().isNotFound());
        poner(admin, "COMMISSION_PCT", "75").andExpect(status().isBadRequest());
        poner(admin, "MIN_BALANCE", "-5").andExpect(status().isBadRequest());
        poner(admin, "MIN_BALANCE", "1500.5").andExpect(status().isBadRequest());
        poner(admin, "COMMISSION_PCT", "8.5").andExpect(status().isOk()); // la comisión admite decimales
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/admin/parameters/MIN_BALANCE").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void eltotalDebeSuperarLaVentanaDePostulacion() throws Exception {
        poner(admin, "MAX_TOTAL_S", "630").andExpect(status().isBadRequest()); // 600 + 60 > 630
        poner(admin, "MAX_TOTAL_S", "660").andExpect(status().isOk());
        poner(admin, "APPLY_WINDOW_S", "700").andExpect(status().isBadRequest());
        poner(admin, "APPLY_WINDOW_S", "120").andExpect(status().isOk());
    }

    @Test
    void elSaldoMinimoEditadoAplicaAlPostularse() throws Exception {
        String pobre = cuenta("TECNICO", "pobre@correo.co");
        recargar(pobre, 30_000);
        long sol = solicitud();
        enviar(pobre, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}").andExpect(status().isUnprocessableEntity());
        poner(admin, "MIN_BALANCE", "25000").andExpect(status().isOk());
        enviar(pobre, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}").andExpect(status().isCreated());
    }

    @Test
    void laComisionEditadaSeCongelaAlElegir() throws Exception {
        poner(admin, "COMMISSION_PCT", "12").andExpect(status().isOk());
        long sol = solicitud();
        long pos = ((Number) campo(enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}"), "$.id")).longValue();
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isOk());
        pedir(hogar, "/api/v1/service-requests/" + sol).andExpect(jsonPath("$.comisionCop").value(2400));
        poner(admin, "COMMISSION_PCT", "20").andExpect(status().isOk()); // no cambia el servicio ya asignado
        pedir(hogar, "/api/v1/service-requests/" + sol).andExpect(jsonPath("$.comisionCop").value(2400));
    }

    @Test
    void lasVentanasEditadasAplicanALasSolicitudesNuevas() throws Exception {
        poner(admin, "APPLY_WINDOW_S", "120").andExpect(status().isOk());
        long sol = solicitud();
        assertThat(jdbc.queryForObject("SELECT expira_en FROM solicitudes WHERE id = ?", java.sql.Timestamp.class, sol).toInstant())
                .isEqualTo(ConfiguracionPruebas.T0.plus(Duration.ofSeconds(120)));
        poner(admin, "SELECT_WINDOW_S", "240").andExpect(status().isOk());
        enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT expira_en FROM solicitudes WHERE id = ?", java.sql.Timestamp.class, sol).toInstant())
                .isEqualTo(ConfiguracionPruebas.T0.plus(Duration.ofSeconds(240)));
    }

    @Test
    void elTopeTotalLimitaLaVentanaDeEleccion() throws Exception {
        poner(admin, "SELECT_WINDOW_S", "600").andExpect(status().isOk());
        poner(admin, "MAX_TOTAL_S", "780").andExpect(status().isOk()); // apply 600 + 180 de margen
        long sol = solicitud();
        reloj.fijar(ConfiguracionPruebas.T0.plus(Duration.ofMinutes(9)));
        enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}").andExpect(status().isCreated());
        // 9 min + 10 min de elección pasaría de los 13 min totales: el tope manda
        assertThat(jdbc.queryForObject("SELECT expira_en FROM solicitudes WHERE id = ?", java.sql.Timestamp.class, sol).toInstant())
                .isEqualTo(ConfiguracionPruebas.T0.plus(Duration.ofSeconds(780)));
    }

    @Test
    void elRadioPorDefectoDeLaZonaSalePorElParametro() throws Exception {
        poner(admin, "SEARCH_RADIUS_M", "8000").andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/technicians/me/location").header("Authorization", "Bearer " + tecnico)
                .contentType("application/json").content("{\"lat\":7.3772,\"lng\":-72.6473}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.radioKm").value(8));
    }
}
