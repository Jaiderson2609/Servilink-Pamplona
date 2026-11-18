package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import co.edu.unipamplona.servilink.apoyo.VerificadorGoogleFalso;
import co.edu.unipamplona.servilink.aplicacion.VerificadorGoogle.Identidad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Ingreso con Google, con el ID de cliente configurado. Nada sale a internet: Google se sustituye por un doble. */
@TestPropertySource(properties = "servilink.google-client-id=cliente-ficticio.apps.googleusercontent.com")
class GoogleTest extends PruebaApi {

    @Autowired VerificadorGoogleFalso google;

    @BeforeEach
    void credenciales() {
        google.credenciales.clear();
        google.credenciales.put("tok-ana", new Identidad("Ana.Gomez@gmail.com", "Ana Gómez"));
    }

    String cuerpo(String credencial, String rol, String telefono, boolean acepta) {
        return "{\"credencial\":\"" + credencial + "\"" + (rol == null ? "" : ",\"rol\":\"" + rol + "\"")
                + (telefono == null ? "" : ",\"telefono\":\"" + telefono + "\"") + ",\"aceptaTerminos\":" + acepta + "}";
    }

    @Test
    void laPantallaPuedeConsultarElIdDeClienteSinSesion() throws Exception {
        pedir(null, "/api/v1/auth/google/config").andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("cliente-ficticio.apps.googleusercontent.com"));
    }

    @Test
    void unaCuentaNuevaNecesitaRolTelefonoYTerminosAntesDeCrearse() throws Exception {
        enviar(null, "/api/v1/auth/google", cuerpo("tok-ana", null, null, false))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("GOOGLE_REGISTRATION_REQUIRED"));
        enviar(null, "/api/v1/auth/google", cuerpo("tok-ana", "HOGAR", "3001234567", false))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("GOOGLE_REGISTRATION_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isZero();
    }

    @Test
    void creaLaCuentaYAbreSesionConCookieDeRenovacion() throws Exception {
        var r = enviar(null, "/api/v1/auth/google", cuerpo("tok-ana", "TECNICO", "3001234567", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("TECNICO"))
                .andExpect(jsonPath("$.nombre").value("Ana Gómez")).andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("servilink_renovacion=")));
        String token = campo(r, "$.accessToken");
        pedir(token, "/api/v1/me").andExpect(status().isOk()).andExpect(jsonPath("$.correo").value("ana.gomez@gmail.com"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billeteras", Integer.class)).isEqualTo(1);
    }

    @Test
    void laCuentaCreadaConGoogleNoTieneUnaClaveConocida() throws Exception {
        enviar(null, "/api/v1/auth/google", cuerpo("tok-ana", "HOGAR", "3001234567", true)).andExpect(status().isOk());
        enviar(null, "/api/v1/auth/login", "{\"correo\":\"ana.gomez@gmail.com\",\"clave\":\"cualquier-clave-1\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unaCuentaExistenteEntraSinPedirNingunDatoYNoSeDuplica() throws Exception {
        enviar(null, "/api/v1/auth/google", cuerpo("tok-ana", "HOGAR", "3001234567", true)).andExpect(status().isOk());
        enviar(null, "/api/v1/auth/google", "{\"credencial\":\"tok-ana\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("HOGAR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isEqualTo(1);
    }

    @Test
    void elCorreoDeUnaCuentaCreadaConClaveEntraALaMismaCuenta() throws Exception {
        cuenta("HOGAR", "ana.gomez@gmail.com");
        enviar(null, "/api/v1/auth/google", "{\"credencial\":\"tok-ana\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("HOGAR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios", Integer.class)).isEqualTo(1);
    }

    @Test
    void unaCredencialQueGoogleNoReconoceNoEntra() throws Exception {
        enviar(null, "/api/v1/auth/google", cuerpo("falsa", "HOGAR", "3001234567", true))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unaCuentaSuspendidaNoEntraConGoogle() throws Exception {
        enviar(null, "/api/v1/auth/google", cuerpo("tok-ana", "HOGAR", "3001234567", true)).andExpect(status().isOk());
        jdbc.update("UPDATE usuarios SET suspendido = true");
        enviar(null, "/api/v1/auth/google", "{\"credencial\":\"tok-ana\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    void noSePuedeElegirElRolAdministrador() throws Exception {
        enviar(null, "/api/v1/auth/google", cuerpo("tok-ana", "ADMIN", "3001234567", true)).andExpect(status().isBadRequest());
        enviar(null, "/api/v1/auth/google", cuerpo("tok-ana", "HOGAR", "abc", true)).andExpect(status().isBadRequest());
    }

    @Test
    void elNombreLargoSeRecortaAlLimiteDeLaCuenta() throws Exception {
        google.credenciales.put("tok-largo", new Identidad("largo@gmail.com", "N".repeat(120)));
        enviar(null, "/api/v1/auth/google", cuerpo("tok-largo", "HOGAR", "3001234567", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("N".repeat(80)));
    }

    @Test
    void laPoliticaDeContenidoDejaCargarElBotonDeGoogle() throws Exception {
        String csp = mvc.perform(MockMvcRequestBuilders.get("/")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).contains("script-src 'self' https://accounts.google.com/gsi/client")
                .contains("style-src 'self' https://accounts.google.com/gsi/style")
                .contains("frame-src https://accounts.google.com/gsi/")
                .contains("https://accounts.google.com/gsi/; object-src 'none'").doesNotContain("unsafe-inline");
    }
}
