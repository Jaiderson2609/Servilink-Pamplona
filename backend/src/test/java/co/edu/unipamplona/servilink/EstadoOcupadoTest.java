package co.edu.unipamplona.servilink;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-008: el técnico pasa a Ocupado al ser elegido y vuelve a Disponible al cerrarse o cancelarse el servicio. */
class EstadoOcupadoTest extends PruebaApi {

    String hogar, tecnico;
    long sol, pos;

    @BeforeEach
    void escenario() throws Exception {
        hogar = cuenta("HOGAR", "hogar@correo.co");
        tecnico = cuenta("TECNICO", "tec@correo.co");
        recargar(tecnico, 50_000);
        sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", solicitudJson("Cambiar un tomacorriente roto")), "$.id")).longValue();
        pos = ((Number) campo(enviar(tecnico, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":30000}"), "$.id")).longValue();
    }

    ResultActions patch(String token, String ruta, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch("/api/v1/service-requests/" + sol + "/" + ruta)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    void estado(String esperado) throws Exception {
        pedir(tecnico, "/api/v1/technicians/me/availability").andExpect(jsonPath("$.estado").value(esperado));
    }

    void elegir() throws Exception {
        estado("AVAILABLE");
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isOk());
        estado("BUSY");
    }

    @Test
    void alSerElegidoQuedaOcupadoYAlConfirmarElTrabajoVuelveADisponible() throws Exception {
        elegir();
        patch(tecnico, "arrive", "{}").andExpect(status().isOk());
        patch(tecnico, "complete", "{}").andExpect(status().isOk());
        estado("BUSY");
        patch(hogar, "confirm", "{}").andExpect(status().isOk());
        estado("AVAILABLE");
    }

    @Test
    void siSeCancelaElServicioAsignadoVuelveADisponible() throws Exception {
        elegir();
        patch(hogar, "cancel", "{\"motivo\":\"Ya no lo necesito\"}").andExpect(status().isOk());
        estado("AVAILABLE");
    }

    @Test
    void siElTecnicoSeDesconectoDuranteElServicioNoSeLeCambiaAlTerminar() throws Exception {
        elegir();
        mvc.perform(MockMvcRequestBuilders.patch("/api/v1/technicians/me/availability").header("Authorization", "Bearer " + tecnico)
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"OFFLINE\"}")).andExpect(status().isOk());
        patch(hogar, "cancel", "{\"motivo\":\"Ya no lo necesito\"}").andExpect(status().isOk());
        estado("OFFLINE");
    }
}
