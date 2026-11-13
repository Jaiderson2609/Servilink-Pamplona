package co.edu.unipamplona.servilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.unipamplona.servilink.apoyo.PruebaApi;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Renovación de sesión (DAD 11.1): token de acceso corto + cookie de renovación opaca, de un solo uso y con rotación. */
class RenovacionSesionTest extends PruebaApi {

    static final String COOKIE = "servilink_renovacion";
    static final String LOGIN = "{\"correo\":\"ana@correo.co\",\"clave\":\"clave-segura-1\"}";

    @BeforeEach
    void escenario() throws Exception {
        cuenta("HOGAR", "ana@correo.co");
        jdbc.update("delete from sesiones_renovacion"); // el ayudante ya inició sesión una vez
    }

    MockHttpServletResponse login() throws Exception {
        return enviar(null, "/api/v1/auth/login", LOGIN).andExpect(status().isOk()).andReturn().getResponse();
    }

    MockHttpServletResponse refrescar(String token) throws Exception {
        var p = post("/api/v1/auth/refresh");
        if (token != null) p.cookie(new Cookie(COOKIE, token));
        return mvc.perform(p).andReturn().getResponse();
    }

    static String valor(MockHttpServletResponse r) {
        var c = r.getCookie(COOKIE);
        return c == null ? null : c.getValue();
    }

    long filas() {
        return jdbc.queryForObject("select count(*) from sesiones_renovacion", Long.class);
    }

    @Test
    void elInicioDeSesionEntregaLaRenovacionEnUnaCookieBlindada() throws Exception {
        var r = login();
        String setCookie = r.getHeader("Set-Cookie");
        assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth",
                "Max-Age=604800");
        assertThat(r.getHeader("Cache-Control")).contains("no-store");
        String token = valor(r);
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        // El valor no viaja en el cuerpo y en la base solo queda su hash.
        assertThat(r.getContentAsString()).doesNotContain(token);
        assertThat(jdbc.queryForObject("select count(*) from sesiones_renovacion where token_hash = ?", Long.class, token)).isZero();
        assertThat(filas()).isEqualTo(1);
    }

    @Test
    void renovarEntregaUnTokenDeAccesoNuevoYRotaLaCookie() throws Exception {
        String t1 = valor(login());
        reloj.fijar(reloj.instant().plusSeconds(600));
        var r = refrescar(t1);
        assertThat(r.getStatus()).isEqualTo(200);
        String t2 = valor(r);
        assertThat(t2).isNotNull().isNotEqualTo(t1);
        String acceso = com.jayway.jsonpath.JsonPath.read(r.getContentAsString(), "$.accessToken");
        assertThat(com.jayway.jsonpath.JsonPath.<Integer>read(r.getContentAsString(), "$.expiresIn")).isEqualTo(900);
        assertThat((String) com.jayway.jsonpath.JsonPath.read(r.getContentAsString(), "$.nombre")).startsWith("Usuario");
        pedir(acceso, "/api/v1/me").andExpect(status().isOk());
        // Las dos filas son de la misma familia; la primera quedó gastada.
        assertThat(jdbc.queryForObject("select count(distinct familia) from sesiones_renovacion", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sesiones_renovacion where usada_en is not null", Long.class)).isEqualTo(1);
        assertThat(refrescar(t2).getStatus()).isEqualTo(200); // el nuevo sigue sirviendo
    }

    @Test
    void reutilizarUnTokenGastadoCierraTodaLaFamilia() throws Exception {
        String t1 = valor(login());
        String t2 = valor(refrescar(t1));
        reloj.fijar(reloj.instant().plusSeconds(60)); // fuera de la ventana de gracia
        assertThat(refrescar(t1).getStatus()).isEqualTo(401); // alguien repite el viejo: posible robo
        assertThat(filas()).isZero();                         // la revocación quedó guardada pese al 401
        assertThat(refrescar(t2).getStatus()).isEqualTo(401); // y el nuevo ya no sirve
    }

    @Test
    void dosPestanasRenovandoAlMismoTiempoNoSeMatan() throws Exception {
        String t1 = valor(login());
        var primera = refrescar(t1);
        var segunda = refrescar(t1); // misma cookie, dentro de la gracia
        assertThat(primera.getStatus()).isEqualTo(200);
        assertThat(segunda.getStatus()).isEqualTo(200);
        assertThat(segunda.getHeader("Set-Cookie")).isNull(); // el navegador conserva la cookie que dio la primera
        assertThat(refrescar(valor(primera)).getStatus()).isEqualTo(200);
    }

    @Test
    void sinCookieOConUnaInventadaSeRechaza() throws Exception {
        var r = refrescar(null);
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getContentAsString()).contains("UNAUTHENTICATED");
        assertThat(refrescar("inventado").getStatus()).isEqualTo(401);
        assertThat(refrescar("x".repeat(500)).getStatus()).isEqualTo(401);
        assertThat(refrescar(" ").getStatus()).isEqualTo(401);
    }

    @Test
    void laRenovacionVenceALos7Dias() throws Exception {
        String t1 = valor(login());
        reloj.fijar(reloj.instant().plus(Duration.ofDays(7)).plusSeconds(1));
        assertThat(refrescar(t1).getStatus()).isEqualTo(401);
        assertThat(filas()).isZero();
    }

    @Test
    void usarlaSeguidoMantieneLaSesionPeroNoPasaDe30Dias() throws Exception {
        Instant inicio = reloj.instant();
        String token = valor(login());
        for (int dia = 6; dia <= 24; dia += 6) {
            reloj.fijar(inicio.plus(Duration.ofDays(dia)));
            var r = refrescar(token);
            assertThat(r.getStatus()).as("día " + dia).isEqualTo(200);
            token = valor(r);
        }
        reloj.fijar(inicio.plus(Duration.ofDays(30)).minusSeconds(60)); // el último token vence al día 30 exacto
        var r = refrescar(token);
        assertThat(r.getStatus()).isEqualTo(200);
        token = valor(r);
        reloj.fijar(inicio.plus(Duration.ofDays(30)).plusSeconds(1));
        assertThat(refrescar(token).getStatus()).isEqualTo(401); // tope de la familia: toca iniciar sesión de nuevo
    }

    @Test
    void unaCuentaSuspendidaNoPuedeRenovar() throws Exception {
        String t1 = valor(login());
        jdbc.update("update usuarios set suspendido = true where correo = 'ana@correo.co'");
        assertThat(refrescar(t1).getStatus()).isEqualTo(401);
        assertThat(filas()).isZero();
    }

    @Test
    void cerrarSesionRevocaLaRenovacionYBorraLaCookie() throws Exception {
        String t1 = valor(login());
        var r = mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, t1))).andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(r.getHeader("Set-Cookie")).contains("Max-Age=0", "HttpOnly", "Path=/api/v1/auth");
        assertThat(filas()).isZero();
        assertThat(refrescar(t1).getStatus()).isEqualTo(401);
        // Cerrar sin cookie o con una inventada no falla.
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, "inventado"))).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, "x".repeat(500)))).andExpect(status().isNoContent());
    }

    @Test
    void cerrarSesionEnUnDispositivoNoCierraLaDeOtro() throws Exception {
        String celular = valor(login());
        String portatil = valor(login());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, celular))).andExpect(status().isNoContent());
        assertThat(refrescar(portatil).getStatus()).isEqualTo(200);
    }

    @Test
    void alIniciarSesionSeLimpianLasRenovacionesVencidas() throws Exception {
        login();
        reloj.fijar(reloj.instant().plus(Duration.ofDays(8)));
        login();
        assertThat(filas()).isEqualTo(1);
    }

    @Test
    void unaClaveIncorrectaNoDejaCookie() throws Exception {
        var r = enviar(null, "/api/v1/auth/login", "{\"correo\":\"ana@correo.co\",\"clave\":\"mala\"}")
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(r.getHeader("Set-Cookie")).isNull();
        assertThat(filas()).isEqualTo(0);
    }
}
