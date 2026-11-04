package co.edu.unipamplona.servilink.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Prueba unitaria sin base de datos: el redondeo de la comisión (ROUND_HALF_UP) y sus bordes. */
class ComisionTest {

    @ParameterizedTest
    @CsvSource({"20000,0.10,2000", "12345,0.10,1235", "12344,0.10,1234", "5,0.10,1", "4,0.10,0", "0,0.10,0",
            "20000,0.08,1600", "20000,0.12,2400"})
    void redondeaMediaHaciaArriba(int precio, String pct, long esperada) {
        assertThat(AsignacionService.comision(precio, new BigDecimal(pct))).isEqualTo(esperada);
    }
}
