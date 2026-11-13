package co.edu.unipamplona.servilink;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Sin credenciales de Cloudinary el servidor lo dice, no firma nada y las fotos siguen yendo a la base. */
@TestPropertySource(properties = {"servilink.cloudinary-cloud-name=", "servilink.cloudinary-api-key=",
        "servilink.cloudinary-api-secret="})
class FotosSinNubeTest extends PruebaApi {

    @Test
    void noHayFirmaNiRegistroPeroLaSubidaALaBaseFunciona() throws Exception {
        String hogar = cuenta("HOGAR", "hogar@correo.co");
        long sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
        enviar(hogar, "/api/v1/uploads/signature", "{\"solicitudId\":" + sol + "}").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPLOAD_UNAVAILABLE"));
        enviar(hogar, "/api/v1/service-requests/" + sol + "/photos/cloudinary",
                "{\"publicId\":\"servilink/solicitudes/" + sol + "/" + "a".repeat(32) + "\",\"version\":\"1\",\"signature\":\"x\",\"format\":\"jpg\"}")
                .andExpect(status().isServiceUnavailable());
        subirFoto(hogar, sol, jpeg(100), "image/jpeg").andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("cloudinary"))));
    }
}
