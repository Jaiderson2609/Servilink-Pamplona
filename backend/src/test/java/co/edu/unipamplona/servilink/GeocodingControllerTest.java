package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.Geocoder.Lugar;
import co.edu.unipamplona.servilink.apoyo.GeocoderFalso;
import co.edu.unipamplona.servilink.apoyo.PruebaIntegracion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** GET /geocoding/search: seguridad, validación del texto y traducción de los errores del proveedor. */
class GeocodingControllerTest extends PruebaIntegracion {

    @Autowired GeocoderFalso geocoder;
    long hogar, tecnico;

    @BeforeEach
    void escenario() {
        geocoder.reiniciar();
        hogar = usuario("HOGAR", "Hogar Uno", "3110000001");
        tecnico = tecnico("Tecnico Demo", 50_000);
    }

    ResultActions buscar(String rol, long id, String q) throws Exception {
        var pedido = get("/api/v1/geocoding/search").header("Authorization", "Bearer " + token(rol, id));
        return mvc.perform(q == null ? pedido : pedido.param("q", q));
    }

    @Test
    void sinSesionNoSePuedeBuscar() throws Exception {
        mvc.perform(get("/api/v1/geocoding/search").param("q", "Carrera 5 Pamplona")).andExpect(status().isUnauthorized());
        assertThat(geocoder.consultas).isEmpty();
    }

    @Test
    void devuelveLosLugaresConSuNombreYCoordenadas() throws Exception {
        geocoder.respuesta = List.of(new Lugar("Carrera 5, Centro, Pamplona, Norte de Santander, Colombia", 7.3772, -72.6473),
                new Lugar("Carrera 5, Cúcuta, Norte de Santander, Colombia", 7.8939, -72.5078));
        buscar("HOGAR", hogar, "Carrera 5 Pamplona").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Carrera 5, Centro, Pamplona, Norte de Santander, Colombia"))
                .andExpect(jsonPath("$[0].lat").value(7.3772))
                .andExpect(jsonPath("$[0].lng").value(-72.6473));
    }

    @Test
    void sinCoincidenciasDevuelveUnArregloVacio() throws Exception {
        buscar("HOGAR", hogar, "zzzz inexistente").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void tambienPuedeBuscarUnTecnicoParaSuZonaDeTrabajo() throws Exception {
        buscar("TECNICO", tecnico, "Pamplona").andExpect(status().isOk());
        assertThat(geocoder.consultas).containsExactly("Pamplona");
    }

    @Test
    void elTextoSeRecortaAntesDeBuscar() throws Exception {
        buscar("HOGAR", hogar, "   Calle 6 Villa del Rosario   ").andExpect(status().isOk());
        assertThat(geocoder.consultas).containsExactly("Calle 6 Villa del Rosario");
    }

    @Test
    void unTextoMuyCortoOLargoOAusenteSeRechazaSinLlamarAlProveedor() throws Exception {
        buscar("HOGAR", hogar, "ab").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        buscar("HOGAR", hogar, "   a   ").andExpect(status().isBadRequest());
        buscar("HOGAR", hogar, "x".repeat(201)).andExpect(status().isBadRequest());
        buscar("HOGAR", hogar, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(geocoder.consultas).isEmpty();
    }

    @Test
    void ellimiteDeTextoEsInclusivo() throws Exception {
        buscar("HOGAR", hogar, "abc").andExpect(status().isOk());
        buscar("HOGAR", hogar, "x".repeat(200)).andExpect(status().isOk());
    }

    @Test
    void conDemasiadasBusquedasEnEsperaResponde429() throws Exception {
        geocoder.fallo = new ErrorNegocio(Codigo.RATE_LIMITED, "Hay muchas búsquedas");
        buscar("HOGAR", hogar, "Pamplona").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void siElProveedorNoResponde503ConUnMensajeEnEspanol() throws Exception {
        geocoder.fallo = new ErrorNegocio(Codigo.GEOCODING_UNAVAILABLE, "El buscador de direcciones no está disponible");
        buscar("HOGAR", hogar, "Pamplona").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GEOCODING_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("El buscador de direcciones no está disponible"));
    }
}
