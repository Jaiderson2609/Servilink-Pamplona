package co.edu.unipamplona.servilink;

import static co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaIntegracion;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AsignacionIntegracionTest extends PruebaIntegracion {

    long hogar, otroHogar, tecnico, sol, pos;

    @BeforeEach
    void escenario() {
        hogar = usuario("HOGAR", "Hogar Uno", "3110000001");
        otroHogar = usuario("HOGAR", "Hogar Dos", "3110000002");
        tecnico = tecnico("Tecnico Demo", 50_000);
        sol = solicitud(hogar, "E02", T0.plus(Duration.ofMinutes(5)));
        pos = postulacion(sol, tecnico, 20_000);
    }

    ResultActions asignar(String token, long solicitudId, String json) throws Exception {
        var req = patch("/api/v1/applications/" + posDe(json) + "/select")
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    @Test
    void asignaRetieneLaComisionYRegistraTodo() throws Exception {
        asignar(token("HOGAR", hogar), sol, cuerpo(pos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitudId").value(sol))
                .andExpect(jsonPath("$.estado").value("E03"))
                .andExpect(jsonPath("$.comisionRetenidaCop").value(2000))
                .andExpect(jsonPath("$.contacto.nombre").value("Tecnico Demo"));

        var fila = jdbc.queryForMap("SELECT estado, tecnico_id, precio_acordado, comision_cop, comision_pct "
                + "FROM solicitudes WHERE id = ?", sol);
        assertThat(fila.get("estado")).isEqualTo("E03");
        assertThat(fila.get("precio_acordado")).isEqualTo(20_000);
        assertThat(fila.get("comision_cop")).isEqualTo(2000);
        assertThat(jdbc.queryForObject("SELECT disponible FROM billeteras WHERE tecnico_id = ?", Long.class, tecnico))
                .isEqualTo(48_000L);
        assertThat(jdbc.queryForObject("SELECT retenido FROM billeteras WHERE tecnico_id = ?", Long.class, tecnico))
                .isEqualTo(2_000L);
        var asiento = jdbc.queryForMap("SELECT tipo, valor, saldo_posterior, referencia FROM libro_contable");
        assertThat(asiento.get("tipo")).isEqualTo("RETENCION");
        assertThat(asiento.get("valor")).isEqualTo(2000L);
        assertThat(asiento.get("saldo_posterior")).isEqualTo(48_000L);
        assertThat(asiento.get("referencia")).isEqualTo("ASIG-" + sol);
        assertThat(notificador.enviados).hasSize(1);
    }

    @Test
    void descartaLasDemasPostulaciones() throws Exception {
        long otro = tecnico("Otro Tecnico", 50_000);
        long posOtro = postulacion(sol, otro, 25_000);
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT estado FROM postulaciones WHERE id = ?", String.class, pos))
                .isEqualTo("ELEGIDA");
        assertThat(jdbc.queryForObject("SELECT estado FROM postulaciones WHERE id = ?", String.class, posOtro))
                .isEqualTo("DESCARTADA");
    }

    @Test
    void elReintentoDelMismoTecnicoEsIdempotente() throws Exception {
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isOk());
        asignar(token("HOGAR", hogar), sol, cuerpo(pos))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comisionRetenidaCop").value(2000));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM libro_contable", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT retenido FROM billeteras WHERE tecnico_id = ?", Long.class, tecnico))
                .isEqualTo(2_000L);
        assertThat(notificador.enviados).hasSize(1);
    }

    @Test
    void sinTokenResponde401ConProblema() throws Exception {
        asignar(null, sol, cuerpo(pos)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tokenConOtraFirmaResponde401() throws Exception {
        asignar(token("HOGAR", hogar) + "x", sol, cuerpo(pos)).andExpect(status().isUnauthorized());
    }

    @Test
    void unTecnicoNoPuedeAsignar() throws Exception {
        asignar(token("TECNICO", tecnico), sol, cuerpo(pos)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unHogarNoPuedeAsignarSolicitudesAjenas() throws Exception {
        asignar(token("HOGAR", otroHogar), sol, cuerpo(pos)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT estado FROM solicitudes WHERE id = ?", String.class, sol))
                .isEqualTo("E02");
    }

    @Test
    void postulacionInexistenteResponde404() throws Exception {
        asignar(token("HOGAR", hogar), sol, cuerpo(999)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void postulacionDeOtroHogarResponde403() throws Exception {
        long hogar3 = usuario("HOGAR", "Hogar Tres", "3110000003");
        long solAjena = solicitud(hogar3, "E02", T0.plus(Duration.ofMinutes(5)));
        long posAjena = postulacion(solAjena, tecnico("Tecnico Ajeno", 50_000), 10_000);
        asignar(token("HOGAR", hogar), sol, cuerpo(posAjena)).andExpect(status().isForbidden());
    }

    @Test
    void postulacionIdQueNoEsNumeroResponde400() throws Exception {
        asignar(token("HOGAR", hogar), sol, "{\"postulacionId\":0}".replace("0", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void enElInstanteExactoDeExpirarResponde410() throws Exception {
        reloj.fijar(T0.plus(Duration.ofMinutes(5)));
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SERVICE_EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT disponible FROM billeteras WHERE tecnico_id = ?", Long.class, tecnico))
                .isEqualTo(50_000L);
    }

    @Test
    void unSegundoAntesDeExpirarAunAsigna() throws Exception {
        reloj.fijar(T0.plus(Duration.ofMinutes(5)).minusSeconds(1));
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isOk());
    }

    @Test
    void unaSolicitudQueNoEstaEnE02Responde409() throws Exception {
        jdbc.update("UPDATE solicitudes SET estado = 'E01' WHERE id = ?", sol);
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void otroTecnicoNoPuedeQuedarseConUnaSolicitudYaAsignada() throws Exception {
        long otro = tecnico("Otro Tecnico", 50_000);
        long posOtro = postulacion(sol, otro, 25_000);
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isOk());
        asignar(token("HOGAR", hogar), sol, cuerpo(posOtro)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT disponible FROM billeteras WHERE tecnico_id = ?", Long.class, otro))
                .isEqualTo(50_000L);
    }

    @Test
    void saldoInsuficienteResponde422() throws Exception {
        jdbc.update("UPDATE billeteras SET disponible = 1999 WHERE tecnico_id = ?", tecnico);
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
        assertThat(jdbc.queryForObject("SELECT estado FROM solicitudes WHERE id = ?", String.class, sol))
                .isEqualTo("E02");
    }

    @Test
    void conElSaldoJustoParaLaComisionAsigna() throws Exception {
        jdbc.update("UPDATE billeteras SET disponible = 2000 WHERE tecnico_id = ?", tecnico);
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT disponible FROM billeteras WHERE tecnico_id = ?", Long.class, tecnico))
                .isEqualTo(0L);
    }

    @Test
    void unTecnicoConUnServicioActivoNoPuedeTomarOtro() throws Exception {
        long hogar3 = usuario("HOGAR", "Hogar Tres", "3110000003");
        long solActiva = solicitud(hogar3, "E02", T0.plus(Duration.ofMinutes(5)));
        long posActiva = postulacion(solActiva, tecnico, 30_000);
        asignar(token("HOGAR", hogar3), solActiva, cuerpo(posActiva)).andExpect(status().isOk());
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TECHNICIAN_UNAVAILABLE"));
    }

    @ParameterizedTest
    @CsvSource({"20000,2000", "12345,1235", "12344,1234", "5,1", "1000000,100000"})
    void laComisionRedondeaMediaHaciaArriba(int precio, int esperada) throws Exception {
        jdbc.update("UPDATE postulaciones SET precio = ? WHERE id = ?", precio, pos);
        jdbc.update("UPDATE billeteras SET disponible = 1000000 WHERE tecnico_id = ?", tecnico);
        asignar(token("HOGAR", hogar), sol, cuerpo(pos))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comisionRetenidaCop").value(esperada));
    }

    @Test
    void elLibroContableNoSePuedeModificarNiBorrar() throws Exception {
        asignar(token("HOGAR", hogar), sol, cuerpo(pos)).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> jdbc.update("UPDATE libro_contable SET valor = 1"));
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> jdbc.update("DELETE FROM libro_contable"));
        assertThat(jdbc.queryForObject("SELECT valor FROM libro_contable", Long.class)).isEqualTo(2000L);
    }
}
