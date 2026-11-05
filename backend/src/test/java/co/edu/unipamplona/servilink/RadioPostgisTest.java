package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas;
import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Búsqueda por radio con PostGIS: distancias reales sobre el elipsoide, no aproximaciones planas. */
class RadioPostgisTest extends PruebaApi {

    // Plaza de Pamplona. Un grado de latitud son ~111,2 km, así que 0,009° ≈ 1 km.
    static final double LAT = 7.3772, LNG = -72.6473;
    String tecnico;

    @BeforeEach
    void escenario() throws Exception {
        tecnico = cuenta("TECNICO", "tec@correo.co");
    }

    long solicitudEn(String correo, double lat, double lng) throws Exception {
        String hogar = cuenta("HOGAR", correo);
        String json = "{\"oficioId\":2,\"descripcion\":\"Cambiar un tomacorriente roto\",\"ubicacion\":{\"lat\":" + lat
                + ",\"lng\":" + lng + "}}";
        return ((Number) campo(enviar(hogar, "/api/v1/solicitudes", json), "$.id")).longValue();
    }

    @Test
    void sinZonaDeTrabajoSeVeTodoSinDistancia() throws Exception {
        long cerca = solicitudEn("a@correo.co", LAT + 0.009, LNG);
        long lejos = solicitudEn("b@correo.co", 7.8939, -72.5078); // Cúcuta, ~60 km
        pedir(tecnico, "/api/v1/solicitudes/abiertas").andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].distanciaM").doesNotExist());
    }

    @Test
    void conRadioDeCincoKmSoloVeLasCercanasYConSuDistancia() throws Exception {
        long cerca = solicitudEn("a@correo.co", LAT + 0.009, LNG);   // ~1 km
        solicitudEn("b@correo.co", LAT + 0.09, LNG);                  // ~10 km
        solicitudEn("c@correo.co", 7.8939, -72.5078);                 // ~60 km
        put(tecnico, "{\"lat\":" + LAT + ",\"lng\":" + LNG + ",\"radioKm\":5}").andExpect(status().isOk());
        var r = pedir(tecnico, "/api/v1/solicitudes/abiertas").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(cerca));
        int d = campo(r, "$[0].distanciaM");
        assertThat(d).isBetween(990, 1010);
    }

    @Test
    void ordenaDeLaMasCercanaALaMasLejana() throws Exception {
        long lejos = solicitudEn("a@correo.co", LAT + 0.09, LNG);     // ~10 km, creada primero
        long cerca = solicitudEn("b@correo.co", LAT + 0.009, LNG);    // ~1 km
        long media = solicitudEn("c@correo.co", LAT + 0.036, LNG);    // ~4 km
        put(tecnico, "{\"lat\":" + LAT + ",\"lng\":" + LNG + ",\"radioKm\":15}").andExpect(status().isOk());
        pedir(tecnico, "/api/v1/solicitudes/abiertas").andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(cerca)).andExpect(jsonPath("$[1].id").value(media))
                .andExpect(jsonPath("$[2].id").value(lejos));
    }

    @Test
    void elRadioSeCambiaYElResultadoLoSigue() throws Exception {
        solicitudEn("a@correo.co", LAT + 0.09, LNG); // ~10 km
        put(tecnico, "{\"lat\":" + LAT + ",\"lng\":" + LNG + ",\"radioKm\":5}");
        pedir(tecnico, "/api/v1/solicitudes/abiertas").andExpect(jsonPath("$.length()").value(0));
        put(tecnico, "{\"lat\":" + LAT + ",\"lng\":" + LNG + ",\"radioKm\":12}");
        pedir(tecnico, "/api/v1/solicitudes/abiertas").andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ubicaciones_tecnico", Integer.class)).isEqualTo(1);
    }

    @Test
    void sinRadioSeUsanCincoKm() throws Exception {
        put(tecnico, "{\"lat\":" + LAT + ",\"lng\":" + LNG + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.radioKm").value(5));
        pedir(tecnico, "/api/v1/tecnicos/yo/ubicacion").andExpect(status().isOk()).andExpect(jsonPath("$.radioKm").value(5));
    }

    @Test
    void noMuestraLasQueYaNoEstanAbiertasNiLasVencidas() throws Exception {
        long asignada = solicitudEn("a@correo.co", LAT + 0.009, LNG);
        solicitudEn("b@correo.co", LAT + 0.018, LNG);
        put(tecnico, "{\"lat\":" + LAT + ",\"lng\":" + LNG + ",\"radioKm\":5}");
        recargar(tecnico, 50_000);
        long pos = ((Number) campo(enviar(tecnico, "/api/v1/solicitudes/" + asignada + "/postulaciones", "{\"precioCop\":20000}"), "$.id")).longValue();
        String hogarA = campo(enviar(null, "/api/v1/auth/login", "{\"correo\":\"a@correo.co\",\"clave\":\"clave-segura-1\"}"), "$.accessToken");
        enviar(hogarA, "/api/v1/solicitudes/" + asignada + "/asignacion", "{\"postulacionId\":" + pos + "}").andExpect(status().isOk());
        pedir(tecnico, "/api/v1/solicitudes/abiertas").andExpect(jsonPath("$.length()").value(1)); // la asignada sale
        reloj.fijar(ConfiguracionPruebas.T0.plus(Duration.ofMinutes(11)));
        String t2 = campo(enviar(null, "/api/v1/auth/login", "{\"correo\":\"tec@correo.co\",\"clave\":\"clave-segura-1\"}"), "$.accessToken");
        pedir(t2, "/api/v1/solicitudes/abiertas").andExpect(jsonPath("$.length()").value(0)); // la otra venció
    }

    @Test
    void validaLaZonaYSoloLaDefineUnTecnico() throws Exception {
        put(tecnico, "{\"lat\":95,\"lng\":-72.6,\"radioKm\":5}").andExpect(status().isBadRequest());
        put(tecnico, "{\"lat\":7.3,\"lng\":-72.6,\"radioKm\":0}").andExpect(status().isBadRequest());
        put(tecnico, "{\"lat\":7.3,\"lng\":-72.6,\"radioKm\":51}").andExpect(status().isBadRequest());
        put(tecnico, "{\"lng\":-72.6}").andExpect(status().isBadRequest());
        String hogar = cuenta("HOGAR", "h@correo.co");
        put(hogar, "{\"lat\":7.3,\"lng\":-72.6,\"radioKm\":5}").andExpect(status().isForbidden());
        pedir(tecnico, "/api/v1/tecnicos/yo/ubicacion").andExpect(status().isNotFound());
    }

    @Test
    void laColumnaGeograficaSeDerivaDeLatYLngYTieneIndiceEspacial() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_solicitudes_punto' "
                + "AND indexdef ILIKE '%gist%'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT PostGIS_Version() IS NOT NULL", Boolean.class)).isTrue();
    }

    org.springframework.test.web.servlet.ResultActions put(String token, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/tecnicos/yo/ubicacion")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json)
                .header("Authorization", "Bearer " + token));
    }
}
