package co.edu.unipamplona.servilink;

import static co.edu.unipamplona.servilink.apoyo.EventoWompi.evento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Sin llaves de Wompi el servidor lo dice y no acredita nada: nunca finge un pago ni acepta eventos sin poder verificarlos. */
@TestPropertySource(properties = {"servilink.wompi-llave-publica=", "servilink.wompi-secreto-integridad=",
        "servilink.wompi-secreto-eventos="})
class RecargaSinWompiTest extends PruebaApi {

    @Test
    void sinLlavesNoSePuedeIniciarUnPago() throws Exception {
        String tecnico = cuenta("TECNICO", "sinwompi@ejemplo.co");
        enviar(tecnico, "/api/v1/wallet/topups", "{\"valorCop\":50000}").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_UNAVAILABLE"));
        pedir(tecnico, "/api/v1/wallet").andExpect(jsonPath("$.pagoDisponible").value(false));
        assertThat(jdbc.queryForObject("select count(*) from recargas", Integer.class)).isZero();
    }

    @Test
    void sinSecretoDeEventosTodoEventoSeRechaza() throws Exception {
        mvc.perform(post("/api/v1/webhooks/wompi").contentType(MediaType.APPLICATION_JSON)
                .content(evento("SL-x", "T-1", "APPROVED", 5_000_000))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }
}
