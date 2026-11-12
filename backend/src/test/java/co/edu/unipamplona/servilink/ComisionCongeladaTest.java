package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RN-14: al asignar, el porcentaje y el valor de la comisión quedan congelados en la solicitud.
 * Cubre el mutante M13 (asignar sin guardar el porcentaje), que la suite anterior no detectaba.
 */
class ComisionCongeladaTest extends PruebaApi {

    String hogar, tecnico;

    @BeforeEach
    void escenario() throws Exception {
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
    }

    @Test
    void alAsignarQuedanCongeladosElPorcentajeYElValor() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        var fila = jdbc.queryForMap("SELECT comision_pct, comision_cop FROM solicitudes WHERE id = ?", sol);
        assertThat((BigDecimal) fila.get("comision_pct")).isEqualByComparingTo("0.10");
        assertThat(((Number) fila.get("comision_cop")).intValue()).isEqualTo(2_000);
    }

    @Test
    void sinAsignarLaSolicitudNoTieneComisionCongelada() throws Exception {
        long sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")),
                "$.id")).longValue();
        var fila = jdbc.queryForMap("SELECT comision_pct, comision_cop FROM solicitudes WHERE id = ?", sol);
        assertThat(fila.get("comision_pct")).isNull();
        assertThat(fila.get("comision_cop")).isNull();
    }

    @Test
    void alConfirmarSeCobraElValorCongeladoAunqueElPorcentajeDeLaFilaCambie() throws Exception {
        long sol = asignado(hogar, tecnico, "Cambiar un tomacorriente roto");
        jdbc.update("UPDATE solicitudes SET comision_pct = 0.50 WHERE id = ?", sol); // simula un cambio posterior de política
        finalizar(hogar, tecnico, sol);
        long cobrado = jdbc.queryForObject("SELECT valor FROM libro_contable WHERE solicitud_id = ? AND tipo = 'COMISION'",
                Long.class, sol);
        assertThat(cobrado).isEqualTo(2_000);
    }
}
