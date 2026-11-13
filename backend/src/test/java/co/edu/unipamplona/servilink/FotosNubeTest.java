package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Fotos en Cloudinary: el servidor firma la subida y verifica la firma de la respuesta; nunca llama a Cloudinary. */
class FotosNubeTest extends PruebaApi {

    static final String SECRETO = "secreto_cloudinary_ficticio";

    @org.springframework.beans.factory.annotation.Autowired co.edu.unipamplona.servilink.aplicacion.FotoService servicioFotos;

    String hogar, tecnico;
    long sol;

    @BeforeEach
    void escenario() throws Exception {
        servicioFotos.reiniciarLimiteDeFirmas();
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
        sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
    }

    static String hash(String algoritmo, String texto) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algoritmo).digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    String firmaSubida() throws Exception {
        return "{\"solicitudId\":" + sol + "}";
    }

    String publicId() throws Exception {
        return campo(enviar(hogar, "/api/v1/uploads/signature", firmaSubida()), "$.publicId");
    }

    String registro(String publicId, String version, String firma, String formato) {
        return "{\"publicId\":\"" + publicId + "\",\"version\":\"" + version + "\",\"signature\":\"" + firma
                + "\",\"format\":\"" + formato + "\"}";
    }

    @Test
    void laFirmaDeSubidaSeCalculaSobreLosParametrosOrdenadosYElSecretoNoSale() throws Exception {
        var r = enviar(hogar, "/api/v1/uploads/signature", firmaSubida()).andExpect(status().isOk())
                .andExpect(jsonPath("$.cloudName").value("nube-de-pruebas"))
                .andExpect(jsonPath("$.apiKey").value("123456789012345"))
                .andExpect(jsonPath("$.allowedFormats").value("jpg,png,webp"))
                .andExpect(jsonPath("$.uploadUrl").value("https://api.cloudinary.com/v1_1/nube-de-pruebas/image/upload"));
        String cuerpo = r.andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain(SECRETO);
        String publicId = campo(r, "$.publicId");
        long marca = ((Number) campo(r, "$.timestamp")).longValue();
        assertThat(publicId).matches("servilink/solicitudes/" + sol + "/[a-f0-9]{32}");
        String esperada = hash("SHA-256", "allowed_formats=jpg,png,webp&public_id=" + publicId + "&timestamp=" + marca + SECRETO);
        assertThat((String) campo(r, "$.signature")).isEqualTo(esperada);
    }

    @Test
    void cadaFirmaTraeUnIdentificadorDistintoQueElClienteNoEligio() throws Exception {
        assertThat(publicId()).isNotEqualTo(publicId());
    }

    @Test
    void soloElHogarDuenoPideFirmas() throws Exception {
        enviar(tecnico, "/api/v1/uploads/signature", firmaSubida()).andExpect(status().isForbidden());
        enviar(cuenta("HOGAR", "otro@correo.co"), "/api/v1/uploads/signature", firmaSubida()).andExpect(status().isForbidden());
        enviar(hogar, "/api/v1/uploads/signature", "{\"solicitudId\":9999}").andExpect(status().isNotFound());
        enviar(hogar, "/api/v1/uploads/signature", "{}").andExpect(status().isBadRequest());
        enviar(null, "/api/v1/uploads/signature", firmaSubida()).andExpect(status().isUnauthorized());
    }

    @Test
    void noSeFirmaCuandoYaNoHayCupo() throws Exception {
        for (int i = 0; i < 3; i++) {
            String id = publicId();
            enviar(hogar, "/api/v1/service-requests/" + sol + "/photos/cloudinary",
                    registro(id, "1700000000", hash("SHA-1", "public_id=" + id + "&version=1700000000" + SECRETO), "jpg"))
                    .andExpect(status().isCreated());
        }
        enviar(hogar, "/api/v1/uploads/signature", firmaSubida()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHOTO_LIMIT_REACHED"));
    }

    @Test
    void noSeFirmaSiLaSolicitudYaNoAdmiteFotos() throws Exception {
        jdbc.update("update solicitudes set estado = 'E04' where id = ?", sol);
        enviar(hogar, "/api/v1/uploads/signature", firmaSubida()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void hayUnLimiteDeFirmasPorHora() throws Exception {
        for (int i = 0; i < 20; i++) {
            enviar(hogar, "/api/v1/uploads/signature", firmaSubida()).andExpect(status().isOk());
        }
        enviar(hogar, "/api/v1/uploads/signature", firmaSubida()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void laFotoSeRegistraConUnaRespuestaFirmadaYSeListaConSuDireccion() throws Exception {
        String id = publicId();
        String firma = hash("SHA-1", "public_id=" + id + "&version=1700000123" + SECRETO);
        var r = enviar(hogar, "/api/v1/service-requests/" + sol + "/photos/cloudinary", registro(id, "1700000123", firma, "png"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.tipoMime").value("image/png"))
                .andExpect(jsonPath("$.url").value("https://res.cloudinary.com/nube-de-pruebas/image/upload/v1700000123/" + id));
        long fotoId = ((Number) campo(r, "$.id")).longValue();
        pedir(hogar, "/api/v1/service-requests/" + sol + "/photos").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].url").value("https://res.cloudinary.com/nube-de-pruebas/image/upload/v1700000123/" + id));
        // La descarga por la API redirige a la nube y conserva el control de permisos.
        pedir(hogar, "/api/v1/service-requests/" + sol + "/photos/" + fotoId).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://res.cloudinary.com/nube-de-pruebas/image/upload/v1700000123/" + id));
        pedir(cuenta("HOGAR", "otro@correo.co"), "/api/v1/service-requests/" + sol + "/photos/" + fotoId)
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from fotos_solicitud where contenido is null", Integer.class)).isEqualTo(1);
    }

    @Test
    void tambienSeAceptaLaFirmaSha256DeLaRespuesta() throws Exception {
        String id = publicId();
        enviar(hogar, "/api/v1/service-requests/" + sol + "/photos/cloudinary",
                registro(id, "1700000123", hash("SHA-256", "public_id=" + id + "&version=1700000123" + SECRETO), "webp"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipoMime").value("image/webp"));
    }

    @Test
    void registrarDosVecesLaMismaFotoNoLaDuplica() throws Exception {
        String id = publicId();
        String cuerpo = registro(id, "1700000123", hash("SHA-1", "public_id=" + id + "&version=1700000123" + SECRETO), "jpg");
        long a = ((Number) campo(enviar(hogar, "/api/v1/service-requests/" + sol + "/photos/cloudinary", cuerpo), "$.id")).longValue();
        long b = ((Number) campo(enviar(hogar, "/api/v1/service-requests/" + sol + "/photos/cloudinary", cuerpo), "$.id")).longValue();
        assertThat(a).isEqualTo(b);
        assertThat(jdbc.queryForObject("select count(*) from fotos_solicitud", Integer.class)).isEqualTo(1);
    }

    @Test
    void unaFirmaFalsaOAjenaSeRechaza() throws Exception {
        String id = publicId();
        String url = "/api/v1/service-requests/" + sol + "/photos/cloudinary";
        enviar(hogar, url, registro(id, "1700000123", hash("SHA-1", "public_id=" + id + "&version=1700000123otro"), "jpg"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        // La firma es de otra versión: no vale para esta.
        enviar(hogar, url, registro(id, "1700000124", hash("SHA-1", "public_id=" + id + "&version=1700000123" + SECRETO), "jpg"))
                .andExpect(status().isUnauthorized());
        enviar(hogar, url, registro(id, "1700000123", "abc", "jpg")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from fotos_solicitud", Integer.class)).isZero();
    }

    @Test
    void noSeRegistraUnaFotoDeOtraSolicitudNiConIdentificadorInventado() throws Exception {
        String url = "/api/v1/service-requests/" + sol + "/photos/cloudinary";
        String ajeno = "servilink/solicitudes/" + (sol + 1) + "/" + "a".repeat(32);
        enviar(hogar, url, registro(ajeno, "1", hash("SHA-1", "public_id=" + ajeno + "&version=1" + SECRETO), "jpg"))
                .andExpect(status().isBadRequest());
        String inventado = "servilink/solicitudes/" + sol + "/mi-foto";
        enviar(hogar, url, registro(inventado, "1", hash("SHA-1", "public_id=" + inventado + "&version=1" + SECRETO), "jpg"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloElHogarDuenoRegistraYElFormatoDebeSerImagen() throws Exception {
        String id = publicId();
        String firma = hash("SHA-1", "public_id=" + id + "&version=5" + SECRETO);
        String url = "/api/v1/service-requests/" + sol + "/photos/cloudinary";
        enviar(tecnico, url, registro(id, "5", firma, "jpg")).andExpect(status().isForbidden());
        enviar(cuenta("HOGAR", "otro@correo.co"), url, registro(id, "5", firma, "jpg")).andExpect(status().isForbidden());
        enviar(hogar, url, registro(id, "5", firma, "gif")).andExpect(status().isUnsupportedMediaType());
        enviar(hogar, url, registro(id, "5", firma, "")).andExpect(status().isUnsupportedMediaType());
        enviar(hogar, url, registro(id, "cinco", firma, "jpg")).andExpect(status().isBadRequest());
        enviar(hogar, "/api/v1/service-requests/9999/photos/cloudinary", registro(id, "5", firma, "jpg")).andExpect(status().isNotFound());
    }

    @Test
    void lasFotosEnLaBaseSiguenFuncionandoYConviveConLasDeLaNube() throws Exception {
        subirFoto(hogar, sol, jpeg(100), "image/jpeg").andExpect(status().isCreated());
        String id = publicId();
        enviar(hogar, "/api/v1/service-requests/" + sol + "/photos/cloudinary",
                registro(id, "9", hash("SHA-1", "public_id=" + id + "&version=9" + SECRETO), "jpg")).andExpect(status().isCreated());
        pedir(hogar, "/api/v1/service-requests/" + sol + "/photos").andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].url").doesNotExist()).andExpect(jsonPath("$[1].url").exists());
    }

    @Test
    void laPoliticaDeSeguridadAutorizaSoloLosOrigenesDeCloudinary() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("img-src 'self' blob: data: https://tile.openstreetmap.org https://res.cloudinary.com;")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("connect-src 'self' https://api.cloudinary.com;")));
    }
}
