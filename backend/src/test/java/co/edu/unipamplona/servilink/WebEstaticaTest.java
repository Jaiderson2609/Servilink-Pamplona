package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.Test;

/** La plataforma web es pública y trae cabeceras de seguridad; la API sigue cerrada. */
class WebEstaticaTest extends PruebaApi {

    @Test
    void laPaginaPrincipalSeSirveSinSesion() throws Exception {
        pedir(null, "/").andExpect(status().isOk()).andExpect(forwardedUrl("index.html")); // la raíz lleva a la página
        var r = pedir(null, "/index.html").andExpect(status().isOk()).andReturn().getResponse();
        assertThat(r.getContentType()).startsWith("text/html");
        assertThat(r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).containsIgnoringCase("<meta charset=\"utf-8\">").contains("ServiLink Pamplona");
    }

    @Test
    void losRecursosPublicosDeLaPwaSeSirvenSinSesionYLoDemasNo() throws Exception {
        pedir(null, "/favicon.svg").andExpect(status().isOk());
        // Archivos que no son de la interfaz no se exponen aunque existan en el classpath.
        pedir(null, "/app.js").andExpect(status().isUnauthorized());
        pedir(null, "/otra-cosa.txt").andExpect(status().isUnauthorized());
    }

    @Test
    void laRespuestaTraeCabecerasDeSeguridad() throws Exception {
        var r = pedir(null, "/index.html").andReturn().getResponse();
        String csp = r.getHeader("Content-Security-Policy");
        assertThat(csp).contains("default-src 'self'").contains("script-src 'self'").contains("object-src 'none'").contains("frame-ancestors 'none'");
        assertThat(csp).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
        assertThat(r.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void laApiSigueExigiendoSesionYLoNoPublicoNoSeExpone() throws Exception {
        pedir(null, "/api/v1/wallet").andExpect(status().isUnauthorized());
        pedir(null, "/api/v1/admin/summary").andExpect(status().isUnauthorized());
        pedir(null, "/application.properties").andExpect(status().isUnauthorized());
        pedir(null, "/db/migration/V1__esquema_base.sql").andExpect(status().isUnauthorized());
    }
}
