package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RF-022: hasta 3 fotos de 2 MB; el formato se verifica por el contenido, no por lo que declara el cliente. */
class FotosTest extends PruebaApi {

    String hogar, tecnico;
    long sol;

    @BeforeEach
    void escenario() throws Exception {
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
        sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
    }

    @Test
    void elHogarSubeUnaFotoYLaDescargaIgual() throws Exception {
        byte[] original = jpeg(500);
        var r = subirFoto(hogar, sol, original, "image/jpeg").andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andExpect(jsonPath("$.tipoMime").value("image/jpeg"))
                .andExpect(jsonPath("$.tamanoBytes").value(500));
        long fotoId = ((Number) campo(r, "$.id")).longValue();
        byte[] bajada = pedir(hogar, "/api/v1/service-requests/" + sol + "/photos/" + fotoId).andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(bajada).isEqualTo(original);
        pedir(hogar, "/api/v1/service-requests/" + sol + "/photos").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(fotoId)).andExpect(jsonPath("$[0].contenido").doesNotExist());
    }

    @Test
    void aceptaPngYWebp() throws Exception {
        subirFoto(hogar, sol, png(), "image/png").andExpect(status().isCreated()).andExpect(jsonPath("$.tipoMime").value("image/png"));
        subirFoto(hogar, sol, webp(), "image/webp").andExpect(status().isCreated()).andExpect(jsonPath("$.tipoMime").value("image/webp"));
    }

    @Test
    void elFormatoSeVerificaPorElContenidoNoPorElTipoDeclarado() throws Exception {
        subirFoto(hogar, sol, "<script>alert(1)</script>".getBytes(), "image/png").andExpect(status().isUnsupportedMediaType());
        subirFoto(hogar, sol, "%PDF-1.4 contenido".getBytes(), "image/jpeg").andExpect(status().isUnsupportedMediaType());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fotos_solicitud", Integer.class)).isZero();
    }

    @Test
    void elLimiteEsDeDosMegabytesPorFotoYTresPorSolicitud() throws Exception {
        subirFoto(hogar, sol, jpeg(2 * 1024 * 1024), "image/jpeg").andExpect(status().isCreated()); // justo en el límite
        subirFoto(hogar, sol, jpeg(2 * 1024 * 1024 + 1), "image/jpeg").andExpect(status().isPayloadTooLarge());
        subirFoto(hogar, sol, new byte[0], "image/jpeg").andExpect(status().isBadRequest());
        subirFoto(hogar, sol, jpeg(100), "image/jpeg").andExpect(status().isCreated());
        subirFoto(hogar, sol, jpeg(100), "image/jpeg").andExpect(status().isCreated());
        subirFoto(hogar, sol, jpeg(100), "image/jpeg").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHOTO_LIMIT_REACHED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fotos_solicitud", Integer.class)).isEqualTo(3);
    }

    @Test
    void soloElHogarDuenoSubeFotos() throws Exception {
        subirFoto(tecnico, sol, jpeg(100), "image/jpeg").andExpect(status().isForbidden());
        String otro = cuenta("HOGAR", "otro@correo.co");
        subirFoto(otro, sol, jpeg(100), "image/jpeg").andExpect(status().isForbidden());
        subirFoto(hogar, 9999, jpeg(100), "image/jpeg").andExpect(status().isNotFound());
    }

    @Test
    void quienPuedeVerLasFotos() throws Exception {
        long fotoId = ((Number) campo(subirFoto(hogar, sol, jpeg(100), "image/jpeg"), "$.id")).longValue();
        String url = "/api/v1/service-requests/" + sol + "/photos/" + fotoId;
        pedir(tecnico, url).andExpect(status().isOk());                      // técnico que puede cotizar (E01)
        pedir(cuenta("HOGAR", "otro@correo.co"), url).andExpect(status().isForbidden()); // otro hogar
        pedir(null, url).andExpect(status().isUnauthorized());
        pedir(hogar, "/api/v1/service-requests/" + sol + "/photos/9999").andExpect(status().isNotFound());

        long pos = ((Number) campo(enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}"), "$.id")).longValue();
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isOk());
        String ajeno = cuenta("TECNICO", "ajeno@correo.co");
        pedir(ajeno, url).andExpect(status().isForbidden());                // ya asignada: el técnico ajeno no la ve
        pedir(tecnico, url).andExpect(status().isOk());                     // el asignado sí
    }

    @Test
    void ElLimiteDeEstadoImpideAgregarFotosAUnServicioFinalizado() throws Exception {
        long s2 = asignado(cuenta("HOGAR", "h2@correo.co"), tecnico, "Revisar la instalación de gas");
        String h2 = campo(enviar(null, "/api/v1/auth/login", "{\"correo\":\"h2@correo.co\",\"clave\":\"clave-segura-1\"}"), "$.accessToken");
        subirFoto(h2, s2, jpeg(100), "image/jpeg").andExpect(status().isCreated()); // E03 todavía permite
        finalizar(h2, tecnico, s2);
        subirFoto(h2, s2, jpeg(100), "image/jpeg").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }
}
