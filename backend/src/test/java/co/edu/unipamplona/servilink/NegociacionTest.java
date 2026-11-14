package co.edu.unipamplona.servilink;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas;
import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-023, RF-020 (retiro) y RF-019 (disponibilidad): negociación de precio entre hogar y técnico. */
class NegociacionTest extends PruebaApi {

    String hogar, tecnico, otro;
    long sol, pos;

    @BeforeEach
    void escenario() throws Exception {
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        otro = cuenta("TECNICO", "otro@correo.co");
        recargar(tecnico, 50_000);
        recargar(otro, 50_000);
        sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
        pos = ((Number) campo(enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":30000}"), "$.id")).longValue();
    }

    String ofertas(long p) { return "/api/v1/applications/" + p + "/offers"; }

    ResultActionsHolder patch(String token, String url) throws Exception {
        var req = MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token);
        return new ResultActionsHolder(mvc.perform(req));
    }

    record ResultActionsHolder(org.springframework.test.web.servlet.ResultActions r) {}

    @Test
    void laPostulacionNaceConSuOfertaInicial() throws Exception {
        pedir(hogar, "/api/v1/service-requests/" + sol + "/applications").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ofertas.length()").value(1))
                .andExpect(jsonPath("$[0].ofertas[0].autor").value("TECNICO"))
                .andExpect(jsonPath("$[0].ofertas[0].montoCop").value(30000));
    }

    @Test
    void contraofertaAceptadaFijaElPrecioYLaAsignacionLoUsa() throws Exception {
        long oferta = ((Number) campo(enviar(hogar, ofertas(pos), "{\"montoCop\":25000}"), "$.id")).longValue();
        // mientras la contraoferta está sin responder el hogar no puede elegir
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isConflict());
        pedir(hogar, "/api/v1/service-requests/" + sol + "/applications").andExpect(jsonPath("$[0].precioCop").value(30000));
        patch(tecnico, "/api/v1/offers/" + oferta + "/accept").r().andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACEPTADA"));
        patch(tecnico, "/api/v1/offers/" + oferta + "/accept").r().andExpect(status().isOk()); // idempotente
        pedir(hogar, "/api/v1/service-requests/" + sol + "/applications").andExpect(jsonPath("$[0].precioCop").value(25000));
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isOk());
        pedir(hogar, "/api/v1/service-requests/" + sol).andExpect(jsonPath("$.precioAcordadoCop").value(25000))
                .andExpect(jsonPath("$.comisionCop").value(2500));
    }

    @Test
    void ofertaFinalDelTecnicoReemplazaLaContraoferta() throws Exception {
        long contra = ((Number) campo(enviar(hogar, ofertas(pos), "{\"montoCop\":20000}"), "$.id")).longValue();
        enviar(tecnico, ofertas(pos), "{\"montoCop\":27000}").andExpect(status().isCreated()).andExpect(jsonPath("$.secuencia").value(3));
        pedir(tecnico, "/api/v1/service-requests/" + sol + "/applications/mine").andExpect(status().isOk())
                .andExpect(jsonPath("$.precioCop").value(27000))
                .andExpect(jsonPath("$.ofertas[1].estado").value("RECHAZADA"));
        patch(tecnico, "/api/v1/offers/" + contra + "/accept").r().andExpect(status().isConflict());
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isOk());
        pedir(hogar, "/api/v1/service-requests/" + sol).andExpect(jsonPath("$.precioAcordadoCop").value(27000));
    }

    @Test
    void respetaLosLimitesDeNegociacion() throws Exception {
        enviar(hogar, ofertas(pos), "{\"montoCop\":20000}").andExpect(status().isCreated());
        enviar(hogar, ofertas(pos), "{\"montoCop\":21000}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEGOTIATION_LIMIT_REACHED"));
        enviar(tecnico, ofertas(pos), "{\"montoCop\":27000}").andExpect(status().isCreated());
        enviar(tecnico, ofertas(pos), "{\"montoCop\":26000}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEGOTIATION_LIMIT_REACHED"));
    }

    @Test
    void elTecnicoNoOfertaSinContraofertaQueResponder() throws Exception {
        enviar(tecnico, ofertas(pos), "{\"montoCop\":27000}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void soloLasPartesNegocianYElMontoSeValida() throws Exception {
        String ajeno = cuenta("HOGAR", "ajeno@correo.co");
        enviar(ajeno, ofertas(pos), "{\"montoCop\":20000}").andExpect(status().isForbidden());
        enviar(otro, ofertas(pos), "{\"montoCop\":20000}").andExpect(status().isForbidden());
        enviar(hogar, ofertas(pos), "{\"montoCop\":10}").andExpect(status().isBadRequest());
        enviar(hogar, ofertas(9999), "{\"montoCop\":20000}").andExpect(status().isNotFound());
        long contra = ((Number) campo(enviar(hogar, ofertas(pos), "{\"montoCop\":20000}"), "$.id")).longValue();
        patch(otro, "/api/v1/offers/" + contra + "/accept").r().andExpect(status().isForbidden());
        patch(hogar, "/api/v1/offers/" + contra + "/accept").r().andExpect(status().isForbidden());
        patch(tecnico, "/api/v1/offers/9999/accept").r().andExpect(status().isNotFound());
    }

    @Test
    void sinVentanaNoSeNegocia() throws Exception {
        long contra = ((Number) campo(enviar(hogar, ofertas(pos), "{\"montoCop\":20000}"), "$.id")).longValue();
        reloj.fijar(ConfiguracionPruebas.T0.plus(Duration.ofMinutes(6)));
        enviar(tecnico, ofertas(pos), "{\"montoCop\":27000}").andExpect(status().isGone());
        patch(tecnico, "/api/v1/offers/" + contra + "/accept").r().andExpect(status().isGone());
    }

    @Test
    void retirarLaPostulacionLaOcultaYImpideElegirla() throws Exception {
        patch(tecnico, "/api/v1/applications/" + pos + "/withdraw").r().andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RETIRADA"));
        patch(tecnico, "/api/v1/applications/" + pos + "/withdraw").r().andExpect(status().isOk()); // idempotente
        pedir(hogar, "/api/v1/service-requests/" + sol + "/applications").andExpect(jsonPath("$.length()").value(0));
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isConflict());
        enviar(hogar, ofertas(pos), "{\"montoCop\":20000}").andExpect(status().isConflict());
    }

    @Test
    void noSeRetiraAjenaNiYaElegida() throws Exception {
        patch(otro, "/api/v1/applications/" + pos + "/withdraw").r().andExpect(status().isForbidden());
        patch(tecnico, "/api/v1/applications/9999/withdraw").r().andExpect(status().isNotFound());
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isOk());
        patch(tecnico, "/api/v1/applications/" + pos + "/withdraw").r().andExpect(status().isConflict());
    }

    @Test
    void lasOfertasSonIdempotentesConClave() throws Exception {
        enviar(hogar, ofertas(pos), "{\"montoCop\":20000}", "clave-oferta-1").andExpect(status().isCreated());
        enviar(hogar, ofertas(pos), "{\"montoCop\":20000}", "clave-oferta-1").andExpect(status().isCreated());
        pedir(hogar, "/api/v1/service-requests/" + sol + "/applications").andExpect(jsonPath("$[0].ofertas.length()").value(2));
    }

    @Test
    void laDisponibilidadDelTecnicoControlaLasSolicitudesYPostulaciones() throws Exception {
        pedir(otro, "/api/v1/technicians/me/availability").andExpect(jsonPath("$.estado").value("AVAILABLE"));
        pedir(otro, "/api/v1/service-requests/open").andExpect(jsonPath("$.length()").value(1));
        patch2(otro, "BUSY").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BUSY"));
        pedir(otro, "/api/v1/service-requests/open").andExpect(jsonPath("$.length()").value(0));
        enviar(otro, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TECHNICIAN_UNAVAILABLE"));
        patch2(otro, "OFFLINE").andExpect(status().isOk());
        patch2(otro, "AVAILABLE").andExpect(status().isOk());
        enviar(otro, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":20000}").andExpect(status().isCreated());
        patch2(otro, "OTRO").andExpect(status().isBadRequest());
        patch2(hogar, "BUSY").andExpect(status().isForbidden());
    }

    org.springframework.test.web.servlet.ResultActions patch2(String token, String estado) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/api/v1/technicians/me/availability")
                .header("Authorization", "Bearer " + token).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\"}"));
    }

    @Test
    void elTecnicoSinPostulacionNoTieneMiPostulacion() throws Exception {
        pedir(otro, "/api/v1/service-requests/" + sol + "/applications/mine").andExpect(status().isNotFound());
        pedir(hogar, "/api/v1/service-requests/" + sol + "/applications/mine").andExpect(status().isForbidden());
    }
}
