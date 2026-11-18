package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Sin GOOGLE_CLIENT_ID la plataforma funciona igual: el botón no se ofrece y la política de contenido no abre nada. */
class GoogleSinConfigurarTest extends PruebaApi {

    @Test
    void sinIdDeClienteLaConfiguracionVieneVaciaYLaPoliticaNoMencionaAGoogle() throws Exception {
        pedir(null, "/api/v1/auth/google/config").andExpect(status().isOk()).andExpect(jsonPath("$.clientId").value(""));
        String csp = mvc.perform(MockMvcRequestBuilders.get("/")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).doesNotContain("google");
    }
}
