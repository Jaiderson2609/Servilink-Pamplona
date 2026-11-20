package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Sin la cuenta de servicio de Firebase la plataforma funciona igual: no se ofrecen avisos y la política no abre nada. */
class PushSinConfigurarTest extends PruebaIntegracion {

    @Test
    void sinCuentaDeServicioLosAvisosNoSeOfreceNiSeGuardaElToken() throws Exception {
        long hogar = usuario("HOGAR", "Hogar Uno", "3110000001");
        String auth = "Bearer " + token("HOGAR", hogar);
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/push/config").header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(false)).andExpect(jsonPath("$.apiKey").doesNotExist());
        mvc.perform(put("/api/v1/me/push-token").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT token_push FROM usuarios WHERE id = ?", String.class, hogar)).isNull();
        String csp = mvc.perform(MockMvcRequestBuilders.get("/")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).doesNotContain("googleapis");
    }
}
