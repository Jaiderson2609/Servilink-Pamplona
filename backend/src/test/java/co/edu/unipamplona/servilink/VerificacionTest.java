package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-010 a RF-012: verificación de identidad de los técnicos con documentos privados. */
class VerificacionTest extends PruebaApi {

    String admin, tecnico, hogar;
    long idTecnico;

    @BeforeEach
    void escenario() throws Exception {
        admin = cuentaAdmin("admin@correo.co");
        tecnico = cuentaSinVerificar("tec@correo.co");
        hogar = cuenta("HOGAR", "hogar@correo.co");
        idTecnico = idDe(tecnico);
    }

    ResultActions enviarDocs(String token, byte[] frente, byte[] reverso, byte[] selfie) throws Exception {
        var req = MockMvcRequestBuilders.multipart("/api/v1/technicians/me/verification").header("Authorization", "Bearer " + token);
        if (frente != null) req.file(new MockMultipartFile("cedulaFrente", "f", "image/jpeg", frente));
        if (reverso != null) req.file(new MockMultipartFile("cedulaReverso", "r", "image/png", reverso));
        if (selfie != null) req.file(new MockMultipartFile("selfie", "s", "image/webp", selfie));
        return mvc.perform(req);
    }

    ResultActions decidir(String token, long id, String decision, String motivo) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/api/v1/admin/technicians/" + id + "/verification")
                .header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"decision\":\"" + decision + "\"" + (motivo == null ? "" : ",\"motivo\":\"" + motivo + "\"") + "}"));
    }

    void enviarCompleto() throws Exception {
        enviarDocs(tecnico, jpeg(500), png(), webp()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void unTecnicoSinVerificarNoVeSolicitudesNiSePostula() throws Exception {
        recargar(tecnico, 50_000);
        long sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
        pedir(tecnico, "/api/v1/service-requests/open").andExpect(jsonPath("$.length()").value(0));
        enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TECHNICIAN_NOT_VERIFIED"));
    }

    @Test
    void elTecnicoEnviaSusDocumentosYQuedanEnRevision() throws Exception {
        pedir(tecnico, "/api/v1/technicians/me/verification").andExpect(jsonPath("$.estado").value("SIN_ENVIAR"));
        enviarCompleto();
        pedir(tecnico, "/api/v1/technicians/me/verification").andExpect(jsonPath("$.estado").value("PENDIENTE"));
        enviarCompleto(); // puede reenviar mientras espera: reemplaza los anteriores
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documentos_verificacion", Integer.class)).isEqualTo(3);
    }

    @Test
    void validaQueEstenLosTresDocumentosYSeanImagenes() throws Exception {
        enviarDocs(tecnico, jpeg(500), png(), null).andExpect(status().isBadRequest());
        enviarDocs(tecnico, jpeg(500), new byte[0], webp()).andExpect(status().isBadRequest());
        enviarDocs(tecnico, jpeg(500), "no soy una imagen".getBytes(), webp()).andExpect(status().isUnsupportedMediaType());
        enviarDocs(tecnico, jpeg(2 * 1024 * 1024 + 1), png(), webp()).andExpect(status().isPayloadTooLarge());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documentos_verificacion", Integer.class)).isZero();
    }

    @Test
    void elAdminAprueba_losDocumentosSeBorranYElTecnicoYaOpera() throws Exception {
        enviarCompleto();
        pedir(admin, "/api/v1/admin/technicians").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].correo").value("tec@correo.co"));
        pedir(admin, "/api/v1/admin/technicians/" + idTecnico + "/documents/SELFIE").andExpect(status().isOk());
        decidir(admin, idTecnico, "APPROVED", null).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documentos_verificacion", Integer.class)).isZero();
        pedir(admin, "/api/v1/admin/technicians/" + idTecnico + "/documents/SELFIE").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_admin WHERE accion = 'APROBAR_TECNICO'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_pendientes WHERE clave LIKE 'VERIF-%'", Integer.class)).isEqualTo(1);

        recargar(tecnico, 50_000);
        long sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
        enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}").andExpect(status().isCreated());
        enviarDocs(tecnico, jpeg(500), png(), webp()).andExpect(status().isConflict()); // ya verificado
    }

    @Test
    void elRechazoExigeMotivoYPermiteReenviar() throws Exception {
        enviarCompleto();
        decidir(admin, idTecnico, "REJECTED", null).andExpect(status().isBadRequest());
        decidir(admin, idTecnico, "REJECTED", "Foto borrosa").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivo").value("Foto borrosa"));
        pedir(tecnico, "/api/v1/technicians/me/verification").andExpect(jsonPath("$.motivo").value("Foto borrosa"));
        decidir(admin, idTecnico, "APPROVED", null).andExpect(status().isConflict()); // ya no está en revisión
        enviarCompleto();
        pedir(tecnico, "/api/v1/technicians/me/verification").andExpect(jsonPath("$.motivo").doesNotExist());
    }

    @Test
    void soloElAdminVeLosDocumentosYDecide() throws Exception {
        enviarCompleto();
        pedir(tecnico, "/api/v1/admin/technicians/" + idTecnico + "/documents/SELFIE").andExpect(status().isForbidden());
        pedir(hogar, "/api/v1/admin/technicians").andExpect(status().isForbidden());
        decidir(tecnico, idTecnico, "APPROVED", null).andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.multipart("/api/v1/technicians/me/verification").header("Authorization", "Bearer " + hogar))
                .andExpect(status().isForbidden());
    }

    @Test
    void validaDecisionTipoYEstadoDelListado() throws Exception {
        decidir(admin, idTecnico, "TAL VEZ", null).andExpect(status().isBadRequest());
        decidir(admin, idTecnico, "APPROVED", null).andExpect(status().isConflict()); // sin envío
        decidir(admin, 99999, "APPROVED", null).andExpect(status().isNotFound());
        decidir(admin, idDe(hogar), "APPROVED", null).andExpect(status().isNotFound()); // no es técnico
        enviarCompleto();
        pedir(admin, "/api/v1/admin/technicians/" + idTecnico + "/documents/OTRO").andExpect(status().isBadRequest());
        pedir(admin, "/api/v1/admin/technicians?estado=XX").andExpect(status().isBadRequest());
        pedir(admin, "/api/v1/admin/technicians?tamano=500").andExpect(status().isBadRequest());
        pedir(admin, "/api/v1/admin/technicians?estado=SIN_ENVIAR").andExpect(jsonPath("$.total").value(0));
    }
}
