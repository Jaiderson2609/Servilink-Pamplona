package co.edu.unipamplona.servilink;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RF-015, RF-016 y CU-07: zona aproximada antes de ser elegido; dirección exacta solo para el técnico elegido. */
class UbicacionServicioTest extends PruebaApi {

    String hogar, elegido, otro;
    long sol, pos;

    @BeforeEach
    void escenario() throws Exception {
        hogar = cuenta("HOGAR", "hogar@correo.co");
        elegido = cuenta("TECNICO", "tec@correo.co");
        otro = cuenta("TECNICO", "otro@correo.co");
        recargar(elegido, 50_000);
        recargar(otro, 50_000);
        String json = "{\"oficioId\":2,\"descripcion\":\"Cambiar un tomacorriente roto\","
                + "\"ubicacion\":{\"lat\":7.377212,\"lng\":-72.647341}}";
        sol = ((Number) campo(enviar(hogar, "/api/v1/service-requests", json), "$.id")).longValue();
        pos = ((Number) campo(enviar(elegido, "/api/v1/service-requests/" + sol + "/applications", "{\"precioCop\":30000}"), "$.id")).longValue();
    }

    @Test
    void antesDeSerElegidoElTecnicoSoloVeLaZonaAproximada() throws Exception {
        pedir(otro, "/api/v1/service-requests/" + sol).andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacion.exacta").value(false))
                .andExpect(jsonPath("$.ubicacion.lat").value(7.375))
                .andExpect(jsonPath("$.ubicacion.lng").value(-72.645));
    }

    @Test
    void elTecnicoElegidoVeLaDireccionExactaQuePusoElHogar() throws Exception {
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isOk());
        pedir(elegido, "/api/v1/service-requests/" + sol).andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacion.exacta").value(true))
                .andExpect(jsonPath("$.ubicacion.lat").value(7.377212))
                .andExpect(jsonPath("$.ubicacion.lng").value(-72.647341));
    }

    @Test
    void elHogarVeSuPropiaDireccionExacta() throws Exception {
        pedir(hogar, "/api/v1/service-requests/" + sol).andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacion.exacta").value(true));
    }

    @Test
    void despuesDeFinalizarOCancelarElTecnicoYaNoVeLaDireccion() throws Exception {
        enviar(hogar, "/api/v1/applications/" + pos + "/select", null).andExpect(status().isOk());
        jdbc.update("UPDATE solicitudes SET estado = 'E05' WHERE id = ?", sol);
        pedir(elegido, "/api/v1/service-requests/" + sol).andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacion").doesNotExist());
    }
}
