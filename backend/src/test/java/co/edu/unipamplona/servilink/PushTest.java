package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.aplicacion.NotificacionService;
import co.edu.unipamplona.servilink.apoyo.PruebaIntegracion;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static co.edu.unipamplona.servilink.apoyo.ConfiguracionPruebas.T0;

/** Avisos push: registro del token por dispositivo y avisos a quien corresponde (técnicos cercanos, hogar, contraparte). */
class PushTest extends PruebaIntegracion {

    // Plaza de Pamplona; 0,009° de latitud ≈ 1 km.
    static final double LAT = 7.3772, LNG = -72.6473;

    @DynamicPropertySource
    static void firebase(DynamicPropertyRegistry r) throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----" + Base64.getEncoder().encodeToString(gen.generateKeyPair().getPrivate().getEncoded())
                + "-----END PRIVATE KEY-----";
        r.add("servilink.firebase-credenciales", () -> "{\"project_id\":\"proyecto-prueba\",\"client_email\":\"c@p.iam.gserviceaccount.com\","
                + "\"private_key\":\"" + pem + "\"}");
        r.add("servilink.firebase-api-key", () -> "clave-web-publica");
        r.add("servilink.firebase-project-id", () -> "proyecto-prueba");
        r.add("servilink.firebase-sender-id", () -> "123456789");
        r.add("servilink.firebase-app-id", () -> "1:123456789:web:abc");
        r.add("servilink.firebase-vapid-key", () -> "BClaveVapidPublica");
    }

    @Autowired NotificacionService servicio;
    long hogar, cerca, lejos, sinToken, ocupado;
    long sol;

    @BeforeEach
    void escenario() {
        hogar = usuario("HOGAR", "Hogar Uno", "3110000001");
        cerca = tecnico("Tecnico Cerca", 50_000);
        lejos = tecnico("Tecnico Lejos", 50_000);
        sinToken = tecnico("Tecnico Sin Token", 50_000);
        ocupado = tecnico("Tecnico Ocupado", 50_000);
        for (long t : new long[] {cerca, lejos, ocupado}) {
            jdbc.update("UPDATE usuarios SET token_push = ? WHERE id = ?", "token-" + t, t);
        }
        jdbc.update("UPDATE usuarios SET disponibilidad = 'OFFLINE' WHERE id = ?", ocupado);
        zona(cerca, LAT, LNG, 5);
        zona(sinToken, LAT, LNG, 5);
        zona(lejos, 7.8939, -72.5078, 5); // Cúcuta, ~60 km
        zona(ocupado, LAT, LNG, 5);
        sol = solicitud(hogar, "E01", T0.plus(Duration.ofMinutes(10)));
        jdbc.update("UPDATE solicitudes SET lat = ?, lng = ? WHERE id = ?", LAT + 0.009, LNG, sol); // ~1 km de la plaza
    }

    void zona(long tecnico, double lat, double lng, int radioKm) {
        jdbc.update("INSERT INTO ubicaciones_tecnico (tecnico_id, lat, lng, radio_km, actualizada_en) VALUES (?, ?, ?, ?, now())",
                tecnico, lat, lng, radioKm);
    }

    String auth(String rol, long id) { return "Bearer " + token(rol, id); }

    String tokenGuardado(long id) {
        return jdbc.queryForObject("SELECT token_push FROM usuarios WHERE id = ?", String.class, id);
    }

    @Test
    void laConfiguracionPublicaSoloSeOfreceConSesionYTraeLosValoresWeb() throws Exception {
        mvc.perform(get("/api/v1/push/config")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/push/config").header("Authorization", auth("HOGAR", hogar))).andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(true)).andExpect(jsonPath("$.projectId").value("proyecto-prueba"))
                .andExpect(jsonPath("$.vapidKey").value("BClaveVapidPublica")).andExpect(jsonPath("$.apiKey").value("clave-web-publica"))
                .andExpect(jsonPath("$.private_key").doesNotExist());
    }

    @Test
    void laPoliticaDeContenidoAbreSoloLosServiciosDeFirebase() throws Exception {
        String csp = mvc.perform(MockMvcRequestBuilders.get("/")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).contains("connect-src 'self'").contains("https://fcmregistrations.googleapis.com")
                .contains("https://firebaseinstallations.googleapis.com").contains("https://fcm.googleapis.com")
                .doesNotContain("unsafe-inline").doesNotContain("*");
    }

    @Test
    void elTokenSeGuardaYSeQuitaAlSalir() throws Exception {
        mvc.perform(put("/api/v1/me/push-token").header("Authorization", auth("HOGAR", hogar))
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"abc123\"}")).andExpect(status().isNoContent());
        assertThat(tokenGuardado(hogar)).isEqualTo("abc123");
        mvc.perform(delete("/api/v1/me/push-token").header("Authorization", auth("HOGAR", hogar))).andExpect(status().isNoContent());
        assertThat(tokenGuardado(hogar)).isNull();
    }

    @Test
    void unTokenVacioOMuyLargoSeRechaza() throws Exception {
        for (String cuerpo : new String[] {"{}", "{\"token\":\"  \"}", "{\"token\":\"" + "x".repeat(4097) + "\"}"}) {
            mvc.perform(put("/api/v1/me/push-token").header("Authorization", auth("HOGAR", hogar))
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void siOtraCuentaIniciaSesionEnElMismoCelularLaAnteriorDejaDeRecibir() throws Exception {
        long otroHogar = usuario("HOGAR", "Hogar Dos", "3110000002");
        for (long id : new long[] {hogar, otroHogar}) {
            mvc.perform(put("/api/v1/me/push-token").header("Authorization", auth("HOGAR", id))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"mismo-celular\"}")).andExpect(status().isNoContent());
        }
        assertThat(tokenGuardado(hogar)).isNull();
        assertThat(tokenGuardado(otroHogar)).isEqualTo("mismo-celular");
    }

    @Test
    void unaSolicitudNuevaAvisaSoloAlTecnicoCercanoVerificadoDisponibleYConAvisoActivo() {
        servicio.avisarNuevaSolicitud(sol);
        assertThat(notificador.enviados).containsExactly("Nueva solicitud cerca de ti");
        assertThat(jdbc.queryForList("SELECT destinatario_id FROM notificaciones_pendientes", Long.class)).containsExactly(cerca);
    }

    @Test
    void unTecnicoSinZonaDefinidaRecibeTodasLasSolicitudes() {
        long sinZona = tecnico("Tecnico Sin Zona", 50_000);
        jdbc.update("UPDATE usuarios SET token_push = 'token-sin-zona' WHERE id = ?", sinZona);
        servicio.avisarNuevaSolicitud(sol);
        assertThat(jdbc.queryForList("SELECT destinatario_id FROM notificaciones_pendientes ORDER BY destinatario_id", Long.class))
                .containsExactly(cerca, sinZona);
    }

    @Test
    void elMismoAvisoNoSeDuplicaSiSeRepite() {
        servicio.avisarNuevaSolicitud(sol);
        servicio.avisarNuevaSolicitud(sol);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_pendientes", Integer.class)).isEqualTo(1);
        assertThat(notificador.enviados).hasSize(1);
    }

    @Test
    void siFirebaseFallaElAvisoQuedaPendienteYSeReintenta() {
        notificador.caido = true;
        servicio.avisarNuevaSolicitud(sol);
        assertThat(jdbc.queryForObject("SELECT estado FROM notificaciones_pendientes", String.class)).isEqualTo("PENDIENTE");
        notificador.caido = false;
        assertThat(servicio.reintentarPendientes()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM notificaciones_pendientes", String.class)).isEqualTo("ENVIADA");
    }

    @Test
    void elHogarRecibeUnAvisoCuandoUnTecnicoSePostula() {
        jdbc.update("UPDATE usuarios SET token_push = 'token-hogar' WHERE id = ?", hogar);
        servicio.avisarPostulacion(sol, cerca);
        assertThat(notificador.enviados).containsExactly("Recibiste una oferta");
        assertThat(jdbc.queryForObject("SELECT destinatario_id FROM notificaciones_pendientes", Long.class)).isEqualTo(hogar);
    }

    @Test
    void sinAvisoPushActivoNoSeGuardaNadaEnLaBandeja() {
        servicio.avisarPostulacion(sol, cerca); // el hogar no activó los avisos
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_pendientes", Integer.class)).isZero();
        assertThat(notificador.intentos).isEmpty();
    }

    @Test
    void laContraofertaDelHogarAvisaAlTecnicoYLaOfertaFinalAvisaAlHogar() {
        jdbc.update("UPDATE usuarios SET token_push = 'token-hogar' WHERE id = ?", hogar);
        long pos = postulacion(sol, cerca, 30_000);
        servicio.avisarOferta(pos, true, 11L);
        servicio.avisarOferta(pos, false, 12L);
        assertThat(notificador.enviados).containsExactly("El hogar respondió con una contraoferta", "El técnico envió su oferta final");
        assertThat(jdbc.queryForList("SELECT destinatario_id FROM notificaciones_pendientes ORDER BY id", Long.class))
                .containsExactly(cerca, hogar);
    }
}
